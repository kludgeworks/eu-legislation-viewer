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
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.PointInTime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jmolecules.jackson3.config.JMoleculesJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.node.ObjectNode;

/**
 * A version's text, on acts and expressions held in memory: 2015/1998 with its original text and
 * two consolidated versions, the latest in German and English only, its English text the golden
 * one, its German one not converted; the version before it still converting, and the original
 * failing in Cellar. The point-in-time view as the application has it, on virtual threads, waiting
 * for a text a moment only.
 */
@WebMvcTest(controllers = TextController.class, properties = "viewer.expressions.wait-for=200ms")
@ImportAutoConfiguration(JMoleculesJacksonAutoConfiguration.class)
@Import(CurieConfiguration.class)
class TextControllerTests {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final String GOLDEN = "06.02.1/02015R1998-20260322";

  @TestConfiguration
  @ComponentScan(basePackageClasses = PointInTime.class)
  static class InMemory {
    @Bean
    Acts acts() {
      var avsec =
          new Act(
              AVSEC,
              new Eli("reg_impl", 2015, "1998"),
              new ResourceType("REG_IMPL"),
              LocalDate.of(2015, 11, 5),
              true,
              null,
              new Timeline(
                  List.of(
                      Version.original(LocalDate.of(2015, 11, 15), AVSEC),
                      consolidated("02015R1998-20251104", LocalDate.of(2025, 11, 4)),
                      consolidated("02015R1998-20260322", LocalDate.of(2026, 3, 22)))));
      return new Acts() {
        @Override
        public Optional<Act> find(ActIdentifier id) {
          return Optional.of(avsec).filter(a -> a.id().equals(id));
        }

        @Override
        public Optional<Act> find(Eli eli) {
          return Optional.of(avsec).filter(a -> eli.equals(a.eli()));
        }
      };
    }

    @Bean
    Expressions expressions() {
      var golden = LegalTextJson.read(GOLDEN);
      var never = new CountDownLatch(1);
      return new Expressions() {
        @Override
        public Optional<Expression> find(ExpressionIdentifier id) {
          return switch (id.toString()) {
            case "02015R1998-20260322 ENG" -> Optional.of(golden);
            case "02015R1998-20251104 ENG" -> {
              try {
                never.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              yield Optional.empty();
            }
            case "32015R1998 ENG" ->
                throw new CellarException("Cellar's Formex for 32015R1998: no such file");
            default -> Optional.empty();
          };
        }

        @Override
        public Set<Language> languages(String document) {
          return document.equals("02015R1998-20260322")
              ? EnumSet.of(Language.DEU, Language.ENG)
              : EnumSet.allOf(Language.class);
        }
      };
    }

    /** Interrupting the conversion still running when the context closes, not waiting on it. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService applicationTaskExecutor() {
      return Executors.newVirtualThreadPerTaskExecutor();
    }

    private static Version consolidated(String celex, LocalDate date) {
      return new Version(Version.Kind.CONSOLIDATED, date, celex, false, List.of());
    }
  }

  @Autowired MockMvcTester mvc;

  @Test
  void aTextIsTheLegalTextWithItsLinks() throws IOException {
    var response =
        mvc.get()
            .uri("/api/acts/32015R1998/versions/2026-03-22/texts/en")
            .accept(MediaTypes.HAL_JSON)
            .exchange();

    assertThat(response).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    assertThat(response).headers().hasValue("Cache-Control", "max-age=3600, public");
    var text =
        (ObjectNode)
            LegalTextJson.JSON.readTree(
                response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    text.remove("_links");
    var golden =
        LegalTextJson.golden(
            LegalTextJson.runfile("kludgeworks_viewer/viewer/src/test/resources/legal-text-json"),
            GOLDEN);
    assertThat(text)
        .isEqualTo(LegalTextJson.JSON.readTree(Files.readString(golden, StandardCharsets.UTF_8)));
  }

  @Test
  void aTextLinksItsVersionTheTextsEitherSideAndItsOtherLanguages() {
    var json =
        assertThat(
                mvc.get()
                    .uri("/api/acts/32015R1998/versions/2026-03-22/texts/en")
                    .accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();

    json.extractingPath("$._links")
        .asMap()
        .containsOnlyKeys(
            "self",
            "up",
            "predecessor-version",
            "first",
            "latest-version",
            "av:changes",
            "alternate",
            "curies");
    json.extractingPath("$._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22/texts/en");
    json.extractingPath("$._links.up.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
    json.extractingPath("$._links.predecessor-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2025-11-04/texts/en");
    json.extractingPath("$._links.first.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2015-11-15/texts/en");
    json.extractingPath("$._links.latest-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22/texts/en");
    json.extractingPath("$._links['av:changes'].href")
        .isEqualTo(
            "http://localhost/api/acts/32015R1998/comparisons/2025-11-04..2026-03-22/texts/en");
    json.extractingPath("$._links.alternate[*].href")
        .asArray()
        .containsExactly(
            "http://localhost/api/acts/32015R1998/versions/2026-03-22/texts/de",
            "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:02015R1998-20260322",
            "http://publications.europa.eu/resource/celex/02015R1998-20260322.ENG");
    json.extractingPath("$._links.alternate[*].hreflang").asArray().containsExactly("de", "en");
    json.extractingPath("$._links.alternate[*].name")
        .asArray()
        .containsExactly("eur-lex", "cellar");
  }

  @Test
  void aTextIsHalAsPlainJsonToo() {
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions/2026-03-22/texts/en")
                .accept(MediaType.APPLICATION_JSON))
        .hasStatusOk()
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.lang")
        .isEqualTo("en");
  }

  @Test
  void aTextStillBeingConvertedIsUnavailableForAMoment() {
    var response =
        mvc.get()
            .uri("/api/acts/32015R1998/versions/2025-11-04/texts/en")
            .accept(MediaTypes.HAL_JSON)
            .exchange();

    assertThat(response).hasStatus(503).hasContentType("application/problem+json");
    assertThat(response).headers().hasValue("Retry-After", "5");
    assertThat(response).headers().hasValue("Cache-Control", "no-store");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("https://kludgeworks.groknull.dev/problems/conversion-in-progress");
  }

  @Test
  void aLanguageTheVersionHasNoTextInIsNotFound() {
    for (var href :
        List.of(
            "/api/acts/32015R1998/versions/2026-03-22/texts/ga",
            "/api/acts/32015R1998/versions/2026-03-22/texts/xx",
            "/api/acts/32015R1998/versions/2026-03-22/texts/de",
            "/api/acts/32015R1998/versions/2026-04-01/texts/en",
            "/api/acts/32099R0001/versions/2026-03-22/texts/en")) {
      assertThat(mvc.get().uri(href).accept(MediaTypes.HAL_JSON))
          .as(href)
          .hasStatus(404)
          .hasContentType("application/problem+json");
    }
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions/2026-03-22/texts/ga")
                .accept(MediaTypes.HAL_JSON))
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("The version of 32015R1998 of 2026-03-22 has no text in ga");
  }

  @Test
  void cellarFailingIsABadGateway() {
    assertThat(
            mvc.get()
                .uri("/api/acts/32015R1998/versions/2015-11-15/texts/en")
                .accept(MediaTypes.HAL_JSON))
        .hasStatus(502)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("https://kludgeworks.groknull.dev/problems/upstream-unavailable");
  }
}
