// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.api.CurieConfiguration;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.jackson3.config.JMoleculesJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * An act as it stood on a date, on acts held in memory: 2015/1998 with its original text and two
 * consolidated versions, the latest in German and English only, and the TFEU. With the API's
 * curies, so relations are named as the application names them.
 */
@WebMvcTest(VersionController.class)
@ImportAutoConfiguration(JMoleculesJacksonAutoConfiguration.class)
@Import(CurieConfiguration.class)
class VersionControllerTests {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final Eli AVSEC_ELI = new Eli("reg_impl", 2015, "1998");
  static final ActIdentifier TFEU = new ActIdentifier("12016E/TXT");
  static final Eli TFEU_ELI = Eli.treaty("tfeu_2016");

  @TestConfiguration
  static class InMemory {
    @Bean
    Acts acts() {
      var avsec =
          new Act(
              AVSEC,
              AVSEC_ELI,
              new ResourceType("REG_IMPL"),
              LocalDate.of(2015, 11, 5),
              true,
              null,
              new Timeline(
                  List.of(
                      Version.original(
                          LocalDate.of(2015, 11, 15), AVSEC, EnumSet.allOf(Language.class)),
                      consolidated("02015R1998-20251104", LocalDate.of(2025, 11, 4)),
                      consolidated("02015R1998-20260322", LocalDate.of(2026, 3, 22)))));
      var tfeu =
          new Act(
              TFEU,
              TFEU_ELI,
              new ResourceType("TREATY"),
              LocalDate.of(2016, 6, 7),
              true,
              null,
              new Timeline(
                  List.of(
                      Version.original(
                          LocalDate.of(2016, 6, 7), TFEU, EnumSet.allOf(Language.class)))));
      var byEli = Map.of(AVSEC_ELI, avsec, TFEU_ELI, tfeu);
      return new Acts() {
        @Override
        public Optional<Act> find(ActIdentifier id) {
          return byEli.values().stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Act> find(Eli eli) {
          return Optional.ofNullable(byEli.get(eli));
        }
      };
    }

    private static Version consolidated(String celex, LocalDate date) {
      return new Version(
          Version.Kind.CONSOLIDATED,
          date,
          celex,
          false,
          List.of(),
          celex.equals("02015R1998-20260322")
              ? EnumSet.of(Language.DEU, Language.ENG)
              : EnumSet.allOf(Language.class));
    }
  }

  @Autowired MockMvcTester mvc;

  @Test
  void aVersionIsAtItsDateWithItsTextInEachLanguageItHas() {
    var response =
        mvc.get()
            .uri("/api/acts/32015R1998/versions/2026-03-22")
            .accept(MediaTypes.HAL_JSON)
            .exchange();

    assertThat(response).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    assertThat(response).headers().doesNotContainHeader("Content-Location");
    assertThat(response).headers().hasValue("Cache-Control", "max-age=3600, public");
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.date").isEqualTo("2026-03-22");
    json.extractingPath("$.celex").isEqualTo("02015R1998-20260322");
    json.extractingPath("$._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
    json.extractingPath("$._links['av:text'][*].href")
        .asArray()
        .containsExactly(
            "http://localhost/api/acts/32015R1998/versions/2026-03-22/texts/de",
            "http://localhost/api/acts/32015R1998/versions/2026-03-22/texts/en");
    json.extractingPath("$._links['av:text'][*].hreflang").asArray().containsExactly("de", "en");
  }

  @Test
  void aVersionLinksItsActAndTheVersionsEitherSideAndTheLatest() {
    var json =
        assertThat(
                mvc.get()
                    .uri("/api/acts/32015R1998/versions/2025-11-04")
                    .accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();

    json.extractingPath("$._links")
        .asMap()
        .containsOnlyKeys(
            "self",
            "version-history",
            "predecessor-version",
            "successor-version",
            "first",
            "latest-version",
            "av:changes",
            "av:text",
            "curies");
    json.extractingPath("$._links.version-history.href")
        .isEqualTo("http://localhost/api/acts/32015R1998");
    json.extractingPath("$._links.predecessor-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2015-11-15");
    json.extractingPath("$._links.successor-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
    json.extractingPath("$._links.first.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2015-11-15");
    json.extractingPath("$._links.latest-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
    json.extractingPath("$._links['av:changes'].href")
        .isEqualTo("http://localhost/api/acts/32015R1998/comparisons/2015-11-15..2025-11-04");
  }

  @Test
  void theFirstAndTheLatestHaveNoVersionBeforeOrAfter() {
    var original =
        assertThat(
                mvc.get()
                    .uri("/api/acts/32015R1998/versions/2015-11-15")
                    .accept(MediaTypes.HAL_JSON))
            .bodyJson();
    var latest =
        assertThat(
                mvc.get()
                    .uri("/api/acts/32015R1998/versions/2026-03-22")
                    .accept(MediaTypes.HAL_JSON))
            .bodyJson();

    original.doesNotHavePath("$._links.predecessor-version");
    original.doesNotHavePath("$._links['av:changes']");
    latest.doesNotHavePath("$._links.successor-version");
  }

  @Test
  void theVersionOnAnyDateIsSeeOtherToItsUrl() {
    var response =
        mvc.get()
            .uri("/api/acts/32015R1998/versions?on=2026-04-01")
            .accept(MediaTypes.HAL_JSON)
            .exchange();

    assertThat(response)
        .hasStatus(HttpStatus.SEE_OTHER)
        .hasHeader("Location", "http://localhost/api/acts/32015R1998/versions/2026-03-22");
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions?on=2026-03-22")
                .accept(MediaTypes.HAL_JSON))
        .hasStatus(HttpStatus.SEE_OTHER)
        .hasHeader("Location", "http://localhost/api/acts/32015R1998/versions/2026-03-22");
  }

  @Test
  void theVersionsAreListedEachWithItsLink() {
    var json =
        assertThat(mvc.get().uri("/api/acts/32015R1998/versions").accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();

    json.extractingPath("$._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions");
    json.extractingPath("$._links.up.href").isEqualTo("http://localhost/api/acts/32015R1998");
    json.extractingPath("$._links.first.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2015-11-15");
    json.extractingPath("$._embedded['av:versions'][*]._links.self.href")
        .asArray()
        .containsExactly(
            "http://localhost/api/acts/32015R1998/versions/2015-11-15",
            "http://localhost/api/acts/32015R1998/versions/2025-11-04",
            "http://localhost/api/acts/32015R1998/versions/2026-03-22");
  }

  @Test
  void aVersionIsHalAsPlainJsonToo() {
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions/2026-03-22")
                .accept(MediaType.APPLICATION_JSON))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
  }

  @Test
  void aTreatysVersionIsUnderItsCelexItsSlashEncoded() {
    var response =
        mvc.get()
            .uri(URI.create("/api/acts/12016E%2FTXT/versions?on=2020-01-01"))
            .accept(MediaTypes.HAL_JSON)
            .exchange();
    assertThat(response)
        .hasStatus(HttpStatus.SEE_OTHER)
        .hasHeader("Location", "http://localhost/api/acts/12016E%2FTXT/versions/2016-06-07");

    var json =
        assertThat(
                mvc.get()
                    .uri(URI.create("/api/acts/12016E%2FTXT/versions/2016-06-07"))
                    .accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();
    json.extractingPath("$._links.version-history.href")
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT");
    json.extractingPath("$._links['av:text']").asArray().hasSize(24);
  }

  @Test
  void aDateThatStartsNoVersionOrIsBeforeTheTextAndAnActNotKnownAreNotFound() {
    assertThat(
            mvc.get().uri("/api/acts/32015R1998/versions/2026-04-01").accept(MediaTypes.HAL_JSON))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo(
            "No version of 32015R1998 starts on 2026-04-01; versions?on=2026-04-01 gives the one"
                + " that applies");
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions?on=2015-01-01")
                .accept(MediaTypes.HAL_JSON))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("The text of 32015R1998 first applies later");
    assertThat(
            mvc.get().uri("/api/acts/32099R0001/versions/2026-01-01").accept(MediaTypes.HAL_JSON))
        .hasStatus(404);
  }

  @Test
  void aDateThatIsNoDayIsABadRequest() {
    assertThat(
            mvc.get().uri("/api/acts/32015R1998/versions/2026-02-30").accept(MediaTypes.HAL_JSON))
        .hasStatus(400);
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions?on=2026-02-30")
                .accept(MediaTypes.HAL_JSON))
        .hasStatus(400);
  }
}
