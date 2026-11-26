// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.HistoryService;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.LawService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the history of an act in the API, with the catalogue mocked. The act is the act of {@link
 * OneActCellar}, as {@code HistoryControllerTests} shows it on the page: the two get the same
 * history.
 */
@WebMvcTest(ApiHistoryController.class)
@Import({
  ApiConfiguration.class,
  CataloguedTesting.class,
  HistoryService.class,
  LawService.class,
  ApiActTesting.Assemblers.class
})
class ApiHistoryControllerTests {
  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;

  @BeforeEach
  void act() {
    when(acts.find(any(ActIdentifier.class))).thenReturn(Optional.empty());
    when(acts.find(any(Eli.class))).thenReturn(Optional.empty());
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
  }

  @Test
  void showsTheStateOfTheActWithItsFactsAndHowEachAmendingActStands() {
    var history =
        assertThat(mvc.get().uri("/api/acts/32015R1998/history/eng/2021-06-01"))
            .hasStatusOk()
            .bodyJson();
    history.extractingPath("$.state.state").isEqualTo("applies");
    history.extractingPath("$.state.appliesFrom").isEqualTo("2016-02-01");
    history
        .extractingPath("$.amendingActs[*].celex")
        .asArray()
        .containsExactly(
            "32026R0449", "32020R0910", "32019R1583", "32021R0255", "32019R0413", "32019R0103");
    history.extractingPath("$.amendingActs[0].state").isEqualTo("not_in_force");
  }

  @Test
  void saysThatTheActIsNotInForceYetWithItsEntryIntoForce() {
    var history =
        assertThat(mvc.get().uri("/api/acts/32015R1998/history/eng/2015-11-01"))
            .hasStatusOk()
            .bodyJson();
    history.extractingPath("$.state.state").isEqualTo("not_in_force");
    history.extractingPath("$.state.entryIntoForce").isEqualTo("2015-11-15");
  }

  @Test
  void showsTheStatesOfTheActForEachDay() {
    var overTime =
        assertThat(mvc.get().uri("/api/acts/32015R1998/history/eng")).hasStatusOk().bodyJson();
    overTime
        .extractingPath("$.state[*].value.state")
        .asArray()
        .containsExactly("not_in_force", "not_applying", "applies");
    overTime
        .extractingPath("$.state[*].from")
        .asArray()
        .containsExactly(null, "2015-11-15", "2016-02-01");
  }

  @Test
  void answersNotFoundForAnActThatNoActHasTheNumberOf() {
    assertThat(mvc.get().uri("/api/acts/32099R9999/history/eng/2021-06-01"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/acts/32099R9999/history/eng")).hasStatus(HttpStatus.NOT_FOUND);
  }
}
