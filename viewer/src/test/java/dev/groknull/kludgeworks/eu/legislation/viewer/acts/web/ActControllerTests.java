// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.ResourceAccessException;

/**
 * An act as HAL at its CELEX, on acts held in memory: an act, a treaty (its CELEX's slash as %2F),
 * an act by its ELI, what no act has, and Cellar failing.
 */
@WebMvcTest(ActController.class)
@ImportAutoConfiguration(JMoleculesJacksonAutoConfiguration.class)
class ActControllerTests {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final Eli AVSEC_ELI = new Eli("reg_impl", 2015, "1998");
  static final ActIdentifier TFEU = new ActIdentifier("12016E/TXT");
  static final Eli TFEU_ELI = Eli.treaty("tfeu_2016");
  static final Eli UNUSABLE = new Eli("reg", 2099, "2");
  static final Eli UNREACHABLE = new Eli("reg", 2099, "3");

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
                      Version.original(LocalDate.of(2015, 11, 15), AVSEC),
                      new Version(
                          Version.Kind.CONSOLIDATED,
                          LocalDate.of(2026, 3, 22),
                          "02015R1998-20260322",
                          true,
                          List.of(new ActIdentifier("32026R0449"))))));
      var tfeu =
          new Act(
              TFEU,
              TFEU_ELI,
              new ResourceType("TREATY"),
              LocalDate.of(2016, 6, 7),
              true,
              null,
              new Timeline(List.of(Version.original(LocalDate.of(2016, 6, 7), TFEU))));
      var byEli = Map.of(AVSEC_ELI, avsec, TFEU_ELI, tfeu);
      return new Acts() {
        @Override
        public Optional<Act> find(ActIdentifier id) {
          return byEli.values().stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Act> find(Eli eli) {
          if (eli.equals(UNUSABLE)) {
            throw new CellarException(
                "Cellar's data for the ELI " + eli + ": several acts have it");
          }
          if (eli.equals(UNREACHABLE)) {
            throw new ResourceAccessException("I/O error on POST request: Connection refused");
          }
          return Optional.ofNullable(byEli.get(eli));
        }
      };
    }
  }

  @Autowired MockMvcTester mvc;

  @Test
  void anActIsAtItsCelexWithItsVersionsAndItsCounterparts() {
    var response = mvc.get().uri("/api/acts/32015R1998").accept(MediaTypes.HAL_JSON).exchange();

    assertThat(response).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    assertThat(response).headers().hasValue("Cache-Control", "max-age=3600, public");
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.celex").isEqualTo("32015R1998");
    json.extractingPath("$.eli").isEqualTo("reg_impl/2015/1998");
    json.extractingPath("$.type").isEqualTo("REG_IMPL");
    json.extractingPath("$.dateOfDocument").isEqualTo("2015-11-05");
    json.extractingPath("$._links.self.href").isEqualTo("http://localhost/api/acts/32015R1998");
    json.extractingPath("$._links['av:versions'].href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions{?on}");
    json.extractingPath("$._links['av:versions'].templated").isEqualTo(true);
    json.extractingPath("$._links.latest-version.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
    json.extractingPath("$._links['av:compare'].href")
        .isEqualTo("http://localhost/api/acts/32015R1998/comparisons{?from,to}");
    json.extractingPath("$._links['av:amendments'].href")
        .isEqualTo("http://localhost/api/acts/32015R1998/amendments");
    json.extractingPath("$._links.alternate[0].href")
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX:32015R1998");
    json.extractingPath("$._links.alternate[0].name").isEqualTo("eur-lex");
    json.extractingPath("$._links.alternate[1].name").isEqualTo("cellar");
    json.extractingPath("$._links.alternate[1].href")
        .isEqualTo("http://publications.europa.eu/resource/celex/32015R1998");
    json.extractingPath("$._embedded['av:versions'][1].celex").isEqualTo("02015R1998-20260322");
    json.extractingPath("$._embedded['av:versions'][1].interim").isEqualTo(true);
    json.extractingPath("$._embedded['av:versions'][1].integrates[0]").isEqualTo("32026R0449");
    json.extractingPath("$._embedded['av:versions'][1]._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998/versions/2026-03-22");
  }

  @Test
  void anActIsHalAsPlainJsonToo() {
    var response =
        mvc.get().uri("/api/acts/32015R1998").accept(MediaType.APPLICATION_JSON).exchange();

    assertThat(response).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
    assertThat(response)
        .bodyJson()
        .extractingPath("$._links.self.href")
        .isEqualTo("http://localhost/api/acts/32015R1998");
  }

  @Test
  void aTreatyIsAtItsCelexItsSlashEncoded() {
    var json =
        assertThat(mvc.get().uri(URI.create("/api/acts/12016E%2FTXT")).accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();

    json.extractingPath("$.celex").isEqualTo("12016E/TXT");
    json.extractingPath("$._links.self.href").isEqualTo("http://localhost/api/acts/12016E%2FTXT");
    json.extractingPath("$._links['av:versions'].href")
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT/versions{?on}");
    json.extractingPath("$._embedded['av:versions'][0]._links.self.href")
        .isEqualTo("http://localhost/api/acts/12016E%2FTXT/versions/2016-06-07");
    json.extractingPath("$._links.alternate[1].href")
        .isEqualTo("http://publications.europa.eu/resource/celex/12016E%2FTXT");
  }

  @Test
  void anActByItsEliIsSeeOtherToItsCelex() {
    for (var eli :
        Map.of("reg_impl/2015/1998", "32015R1998", "treaty/tfeu_2016", "12016E%2FTXT").entrySet()) {
      assertThat(mvc.get().uri("/api/acts?eli={eli}", eli.getKey()).accept(MediaTypes.HAL_JSON))
          .hasStatus(HttpStatus.SEE_OTHER)
          .hasHeader("Location", "http://localhost/api/acts/" + eli.getValue());
    }
  }

  @Test
  void aCelexOrAnEliNoActHasIsNotFoundAsAProblem() {
    var response = mvc.get().uri("/api/acts/32099R0001").accept(MediaTypes.HAL_JSON).exchange();

    assertThat(response).hasStatus(404).hasContentType("application/problem+json");
    assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo("No act 32099R0001");
    assertThat(mvc.get().uri("/api/acts?eli=reg/2099/1").accept(MediaTypes.HAL_JSON))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("No act has the ELI reg/2099/1");
  }

  @Test
  void whatIsNoCelexOrNoEliIsABadRequest() {
    assertThat(mvc.get().uri("/api/acts/R1998").accept(MediaTypes.HAL_JSON)).hasStatus(400);
    assertThat(mvc.get().uri("/api/acts?eli=reg/15/1998").accept(MediaTypes.HAL_JSON))
        .hasStatus(400);
  }

  @Test
  void cellarFailingIsABadGatewayOfItsOwnProblemType() {
    for (var eli : List.of(UNUSABLE, UNREACHABLE)) {
      var response =
          mvc.get().uri("/api/acts?eli={eli}", eli.path()).accept(MediaTypes.HAL_JSON).exchange();

      assertThat(response).hasStatus(502).hasContentType("application/problem+json");
      var json = assertThat(response).bodyJson();
      json.extractingPath("$.type")
          .isEqualTo("https://kludgeworks.groknull.dev/problems/upstream-unavailable");
      json.extractingPath("$.detail").asString().doesNotContain("several acts", "refused");
    }
  }
}
