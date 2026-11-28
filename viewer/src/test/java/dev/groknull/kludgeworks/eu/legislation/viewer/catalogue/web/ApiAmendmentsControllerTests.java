// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests what other acts did to the provisions of an act, in the API, with the catalogue mocked. The
 * act is the act of {@link OneActCellar}.
 */
@WebMvcTest(ApiAmendmentsController.class)
@Import({
  ApiConfiguration.class,
  CataloguedTesting.class,
  ApiActTesting.OnTheFirstOfJune2021.class,
  ApiActTesting.Assemblers.class
})
class ApiAmendmentsControllerTests {
  @Autowired MockMvcTester mvc;
  @MockitoBean Acts acts;
  @MockitoBean Texts texts;

  @BeforeEach
  void act() {
    when(acts.find(any(ActIdentifier.class))).thenReturn(Optional.empty());
    when(acts.find(any(Eli.class))).thenReturn(Optional.empty());
    when(acts.findByVersion(any())).thenReturn(Optional.empty());
    when(acts.find(OneActCellar.CELEX)).thenReturn(Optional.of(OneActCellar.ACT));
    when(acts.find(OneActCellar.ELI)).thenReturn(Optional.of(OneActCellar.ACT));
  }

  @Test
  void showsTheAmendmentsWithTheOwnDatesOfTheAmendingActs() throws UnsupportedEncodingException {
    var act = OneActCellar.ACT;
    var amendment =
        new Amendment(
            new ActIdentifier("32019R0103"),
            new Location("AN PO 11.1"),
            new Place("annex point 11.1"),
            Amendment.Role.REPLACEMENT,
            LocalDate.of(2019, 2, 1),
            null,
            Set.of());
    when(acts.find(OneActCellar.CELEX))
        .thenReturn(
            Optional.of(
                new Act(
                    act.id(),
                    act.eli(),
                    act.type(),
                    act.titles(),
                    act.dateOfDocument(),
                    act.inForce(),
                    act.endOfValidity(),
                    List.of(),
                    act.timeline(),
                    act.dates(),
                    List.of(amendment),
                    act.publications())));

    var amendments = json(mvc.get().uri("/api/acts/32015R1998/amendments").exchange());

    assertThat(amendments.at("/amendments/0/act").asString()).isEqualTo("32019R0103");
    assertThat(amendments.at("/amendments/0/cited").asString()).isEqualTo("2019/103");
    assertThat(amendments.at("/amendments/0/location").asString()).isEqualTo("AN PO 11.1");
    assertThat(amendments.at("/amendments/0/role").asString()).isEqualTo("replacement");
    assertThat(amendments.at("/amendments/0/from").asString()).isEqualTo("2019-02-01");
    assertThat(amendments.at("/dates/32019R0103/1/date").asString()).isEqualTo("2021-12-31");
    assertThat(amendments.at("/dates/32019R0103/1/kind").asString()).isEqualTo("application");
    assertThat(amendments.at("/dates/32019R0103/1/forPart").asBoolean()).isTrue();
    assertThat(amendments.at("/dates/32019R0103/1/clause").asString())
        .isEqualTo("MA/PART V ART 2 ET 32020R0910");
    assertThat(amendments.at("/dates/32019R0103/1/source").asString()).isEqualTo("cellar");
    assertThat(amendments.at("/_links/up/href").asString()).isEqualTo(ACT);
  }
}
