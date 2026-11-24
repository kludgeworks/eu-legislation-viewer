// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.json;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the law of an act on a day in the API, with the catalogue mocked. The act is the act of
 * {@link OneActCellar}.
 */
@WebMvcTest(ApiLawController.class)
@Import({ApiConfiguration.class, CataloguedTesting.class, ApiActTesting.OnTheFirstOfJune2021.class})
class ApiLawControllerTests {
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
  void showsTheLawOnTheDayAskedWithTheVersionThatAppliesAndTheDayItAppliesUntil()
      throws UnsupportedEncodingException {
    var law = json(mvc.get().uri("/api/acts/32015R1998/on/2022-02-14").exchange());

    assertThat(law.at("/day").asString()).isEqualTo("2022-02-14");
    assertThat(law.at("/until").asString()).isEqualTo("2026-03-22");
    // 2015/1998 applies in full: its application is not deferred.
    assertThat(law.at("/deferredApplication").isNull()).isTrue();
    assertThat(texts(law.at("/_embedded/av:version"), "/version")).containsExactly("2021-12-31");
    assertThat(law.at("/_links/self/href").asString()).isEqualTo(ACT + "/on/2022-02-14");
    assertThat(law.at("/_links/up/href").asString()).isEqualTo(ACT);
  }

  @Test
  void saysWhenTheTextFirstAppliesForADayBeforeTheFirstVersion()
      throws UnsupportedEncodingException {
    var result = mvc.get().uri("/api/acts/32015R1998/on/2015-11-10").exchange();
    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    var problem = json(result);

    assertThat(problem.at("/type").asString())
        .isEqualTo("http://localhost/api/docs#before-first-version");
    assertThat(problem.at("/title").asString()).isEqualTo("Before the first version");
    assertThat(problem.at("/day").asString()).isEqualTo("2015-11-10");
    assertThat(problem.at("/dateOfDocument").asString()).isEqualTo("2015-11-09");
    assertThat(problem.at("/firstApplies").asString()).isEqualTo("2015-11-30");
    assertThat(problem.at("/firstVersion").asString()).isEqualTo(ACT + "/versions/oj");
  }
}
