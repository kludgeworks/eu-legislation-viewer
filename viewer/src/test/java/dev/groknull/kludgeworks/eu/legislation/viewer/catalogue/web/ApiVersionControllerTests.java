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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests the versions of an act in the API, with the catalogue mocked. The act is the act of {@link
 * OneActCellar}, and today is 1 June 2021, when its consolidation of 15 March 2021 applies.
 */
@WebMvcTest(ApiVersionController.class)
@Import({ApiConfiguration.class, CataloguedTesting.class, ApiActTesting.OnTheFirstOfJune2021.class})
class ApiVersionControllerTests {
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
  void listsTheVersionsFromTheOriginalText() throws UnsupportedEncodingException {
    var versions = json(mvc.get().uri("/api/acts/32015R1998/versions").exchange());

    assertThat(texts(versions.at("/_embedded/item"), "/version"))
        .containsExactly("oj", "2021-03-15", "2021-12-31", "2026-03-22", "2026-10-01");
    assertThat(versions.at("/_embedded/item/1/_links/self/href").asString())
        .isEqualTo(ACT + "/versions/2021-03-15");
    assertThat(versions.at("/_links/up/href").asString()).isEqualTo(ACT);
    assertThat(versions.at("/_links/first/href").asString()).isEqualTo(ACT + "/versions/oj");
    assertThat(versions.at("/_links/latest-version/href").asString())
        .isEqualTo(ACT + "/versions/2021-03-15");
  }

  @Test
  void showsAVersionWithItsNeighbours() throws UnsupportedEncodingException {
    var version = json(mvc.get().uri("/api/acts/32015R1998/versions/2021-03-15").exchange());

    assertThat(version.at("/kind").asString()).isEqualTo("consolidated");
    assertThat(version.at("/date").asString()).isEqualTo("2021-03-15");
    assertThat(version.at("/celex").asString()).isEqualTo("02015R1998-20210315");
    assertThat(version.at("/interim").asBoolean()).isFalse();
    assertThat(texts(version.at("/integrates"), "")).containsExactly("32019R0413", "32021R0255");
    assertThat(texts(version.at("/languages"), "")).containsExactly("eng", "fra");
    assertThat(version.at("/_links/up/href").asString()).isEqualTo(ACT);
    assertThat(version.at("/_links/version-history/href").asString()).isEqualTo(ACT + "/versions");
    assertThat(version.at("/_links/predecessor-version/href").asString())
        .isEqualTo(ACT + "/versions/oj");
    assertThat(version.at("/_links/successor-version/href").asString())
        .isEqualTo(ACT + "/versions/2021-12-31");
    assertThat(texts(version.at("/_links/av:text"), "/href"))
        .containsExactly(
            ACT + "/versions/2021-03-15/texts/eng", ACT + "/versions/2021-03-15/texts/fra");
    assertThat(texts(version.at("/_links/av:text"), "/hreflang")).containsExactly("en", "fr");
  }

  @Test
  void showsTheOriginalTextAtOjWithoutAVersionBeforeIt() throws UnsupportedEncodingException {
    var version = json(mvc.get().uri("/api/acts/32015R1998/versions/oj").exchange());

    assertThat(version.at("/kind").asString()).isEqualTo("original");
    assertThat(version.at("/date").asString()).isEqualTo("2015-11-30");
    assertThat(version.at("/_links/predecessor-version").isMissingNode()).isTrue();
    // The day of the original text is not its address: only a consolidation is at a date.
    assertThat(mvc.get().uri("/api/acts/32015R1998/versions/2015-11-30"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }
}
