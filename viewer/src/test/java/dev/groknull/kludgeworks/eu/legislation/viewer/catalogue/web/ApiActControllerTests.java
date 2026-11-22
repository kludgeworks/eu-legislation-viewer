// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the resources of an act in the API, with the catalogue mocked. The act is the act of {@link
 * OneActCellar}, and today is 1 June 2021, when its consolidation of 15 March 2021 applies.
 */
@WebMvcTest(ApiActController.class)
@Import({
  ApiConfiguration.class,
  CataloguedTesting.class,
  ApiActControllerTests.OnTheFirstOfJune2021.class
})
class ApiActControllerTests {
  private static final String ACT = "http://localhost/api/acts/32015R1998";

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
    assertThat(act.at("/_links/av:on/href").asString()).isEqualTo(ACT + "/on/{date}");
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
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT/on/{date}");
  }

  @Test
  void doesNotFindAnActThatTheCatalogueDoesNotKnow() {
    assertThat(mvc.get().uri("/api/acts/32099R0001"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
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

  @Test
  void showsTheLawOnTheDayAskedWithTheVersionThatAppliesAndTheDayItAppliesUntil()
      throws UnsupportedEncodingException {
    var law = json(mvc.get().uri("/api/acts/32015R1998/on/2022-02-14").exchange());

    assertThat(law.at("/date").asString()).isEqualTo("2022-02-14");
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
    assertThat(problem.at("/date").asString()).isEqualTo("2015-11-10");
    assertThat(problem.at("/dateOfDocument").asString()).isEqualTo("2015-11-09");
    assertThat(problem.at("/firstApplies").asString()).isEqualTo("2015-11-30");
    assertThat(problem.at("/firstVersion").asString()).isEqualTo(ACT + "/versions/oj");
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

  @Test
  void showsTheAmendmentsWithTheOwnDatesOfTheAmendingActs() throws UnsupportedEncodingException {
    var act = OneActCellar.ACT;
    var amendment =
        new Amendment(
            new ActIdentifier("32019R0103"),
            new Location("AN PO 11.1"),
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
                    act.timeline(),
                    act.dates(),
                    act.amendingDates(),
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
    assertThat(amendments.at("/dates/32019R0103/1/partial").asBoolean()).isTrue();
    assertThat(amendments.at("/dates/32019R0103/1/clause").asString())
        .isEqualTo("MA/PART V ART 2 ET 32020R0910");
    assertThat(amendments.at("/_links/up/href").asString()).isEqualTo(ACT);
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
        act.timeline(),
        act.dates(),
        act.amendingDates(),
        act.amendments(),
        act.publications());
  }

  private static List<String> texts(JsonNode array, String pointer) {
    return StreamSupport.stream(array.spliterator(), false)
        .map(each -> (pointer.isEmpty() ? each : each.at(pointer)).asString())
        .toList();
  }

  private static JsonNode json(MvcTestResult result) throws UnsupportedEncodingException {
    return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
  }

  /** Today is 1 June 2021. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OnTheFirstOfJune2021 {
    @Bean
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
