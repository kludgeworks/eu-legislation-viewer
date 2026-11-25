// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.ApiActTesting.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.groknull.kludgeworks.eu.legislation.viewer.ApiConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Tests an act in the API and its lookup, with the catalogue mocked. The act is the act of {@link
 * OneActCellar}, and today is 1 June 2021, when its consolidation of 15 March 2021 applies.
 */
@WebMvcTest(ApiActController.class)
@Import({ApiConfiguration.class, CataloguedTesting.class, ApiActTesting.OnTheFirstOfJune2021.class})
class ApiActControllerTests {
  /** A treaty, whose CELEX number has a {@code /}. */
  private static final ActIdentifier TREATY = new ActIdentifier("12016E/TXT");

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
    when(acts.find(TREATY)).thenReturn(Optional.of(treaty()));
  }

  @Test
  void looksAnActUpByItsCelexNumberOrItsEli() {
    for (var lookup :
        new String[] {
          "/api/acts?celex=32015R1998",
          "/api/acts?eli=reg_impl/2015/1998",
          "/api/acts?eli=http://data.europa.eu/eli/reg_impl/2015/1998/oj"
        }) {
      assertThat(mvc.get().uri(lookup))
          .as(lookup)
          .hasStatus(HttpStatus.SEE_OTHER)
          .headers()
          .hasValue(HttpHeaders.LOCATION, ACT)
          .hasValue(HttpHeaders.CACHE_CONTROL, "max-age=3600");
    }
  }

  @Test
  void looksAVersionUpByTheCelexNumberOfItsConsolidation() {
    when(acts.findByVersion("02015R1998-20260322")).thenReturn(Optional.of(OneActCellar.ACT));

    assertThat(mvc.get().uri("/api/acts?celex=02015R1998-20260322"))
        .hasStatus(HttpStatus.SEE_OTHER)
        .headers()
        .hasValue(HttpHeaders.LOCATION, ACT + "/versions/2026-03-22");
    assertThat(mvc.get().uri("/api/acts?celex=02099R0001-20260322"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void refusesALookupThatNamesNoAct() {
    assertThat(mvc.get().uri("/api/acts?celex=32099R0001")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/acts?eli=reg/2099/1")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/acts?celex=not a number")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(mvc.get().uri("/api/acts?eli=/reg//1")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void showsTheActWithLinksToItsVersionsAndToTheLawOnADate() throws UnsupportedEncodingException {
    var result = mvc.get().uri("/api/acts/32015R1998").accept(MediaTypes.HAL_JSON).exchange();
    assertThat(result).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    var act = json(result);

    assertThat(act.at("/celex").asString()).isEqualTo("32015R1998");
    assertThat(act.at("/eli").asString()).isEqualTo("reg_impl/2015/1998");
    assertThat(act.at("/type").asString()).isEqualTo("REG_IMPL");
    assertThat(act.at("/titles/eng").asString()).startsWith("Commission Implementing Regulation");
    assertThat(act.at("/dateOfDocument").asString()).isEqualTo("2015-11-09");
    assertThat(act.at("/inForce").asBoolean()).isTrue();
    assertThat(act.at("/_links/self/href").asString()).isEqualTo(ACT);
    assertThat(act.at("/_links/version-history/href").asString()).isEqualTo(ACT + "/versions");
    assertThat(act.at("/_links/av:on/href").asString()).isEqualTo(ACT + "/on/{day}");
    assertThat(act.at("/_links/av:on/templated").asBoolean()).isTrue();
    assertThat(act.at("/_links/av:amendments/href").asString()).isEqualTo(ACT + "/amendments");
  }

  @Test
  void linksToTheVersionThatAppliesTodayAsTheLatest() throws UnsupportedEncodingException {
    var act = json(mvc.get().uri("/api/acts/32015R1998").exchange());

    assertThat(act.at("/_links/latest-version/href").asString())
        .isEqualTo(ACT + "/versions/2021-03-15");
  }

  @Test
  void keepsTheSlashOfACelexNumberInItsSegment() throws UnsupportedEncodingException {
    var result = mvc.get().uri(URI.create("/api/acts/12016E%2FTXT")).exchange();
    assertThat(result).hasStatusOk();
    var act = json(result);

    assertThat(act.at("/celex").asString()).isEqualTo("12016E/TXT");
    assertThat(act.at("/_links/self/href").asString())
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT");
    assertThat(act.at("/_links/version-history/href").asString())
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT/versions");
    assertThat(act.at("/_links/av:on/href").asString())
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT/on/{day}");
  }

  @Test
  void doesNotFindAnActThatTheCatalogueDoesNotKnow() {
    assertThat(mvc.get().uri("/api/acts/32099R0001"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void answersWhatAClientAlreadyHasWithNotModified() {
    var first = mvc.get().uri("/api/acts/32015R1998").exchange();
    assertThat(first).hasStatusOk().headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");
    var tag = Objects.requireNonNull(first.getResponse().getHeader(HttpHeaders.ETAG));
    assertThat(tag).startsWith("W/\"");

    assertThat(mvc.get().uri("/api/acts/32015R1998").header(HttpHeaders.IF_NONE_MATCH, tag))
        .hasStatus(HttpStatus.NOT_MODIFIED);
  }

  private static Act treaty() {
    var act = OneActCellar.ACT;
    return new Act(
        TREATY,
        null,
        act.type(),
        act.titles(),
        act.dateOfDocument(),
        act.inForce(),
        act.endOfValidity(),
        List.of(),
        act.timeline(),
        new ActDates(TREATY, act.dates().own(), act.dates().amending()),
        act.amendments(),
        act.publications());
  }
}
