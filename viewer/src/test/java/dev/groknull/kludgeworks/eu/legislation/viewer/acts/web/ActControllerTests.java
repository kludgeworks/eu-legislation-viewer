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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.ResourceAccessException;

/**
 * An act as HAL at its ELI, on acts held in memory: the act's and the treaty's ELI shapes, an ELI
 * no act has, and Cellar failing.
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
  void anActIsAtItsEliWithItsVersionsAndItsCounterparts() {
    var response = mvc.get().uri("/eli/reg_impl/2015/1998").accept(MediaTypes.HAL_JSON).exchange();

    assertThat(response).hasStatusOk().hasContentType(MediaTypes.HAL_JSON);
    assertThat(response).headers().hasValue("Cache-Control", "max-age=3600, public");
    var json = assertThat(response).bodyJson();
    json.extractingPath("$.celex").isEqualTo("32015R1998");
    json.extractingPath("$.eli").isEqualTo("reg_impl/2015/1998");
    json.extractingPath("$.type").isEqualTo("REG_IMPL");
    json.extractingPath("$.dateOfDocument").isEqualTo("2015-11-05");
    json.extractingPath("$._links.self.href").isEqualTo("http://localhost/eli/reg_impl/2015/1998");
    json.extractingPath("$._links.alternate[0].href")
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX:32015R1998");
    json.extractingPath("$._links.alternate[0].name").isEqualTo("eur-lex");
    json.extractingPath("$._links.alternate[0].type").isEqualTo("text/html");
    json.extractingPath("$._links.alternate[0].hreflang").isEqualTo("en");
    json.extractingPath("$._links.alternate[1].name").isEqualTo("cellar");
    json.extractingPath("$._links.alternate[1].href")
        .isEqualTo("http://publications.europa.eu/resource/celex/32015R1998");
    json.extractingPath("$._embedded['av:versions'][1].celex").isEqualTo("02015R1998-20260322");
    json.extractingPath("$._embedded['av:versions'][1].interim").isEqualTo(true);
    json.extractingPath("$._embedded['av:versions'][1].integrates[0]").isEqualTo("32026R0449");
  }

  @Test
  void aTreatyIsAtItsEditionsEli() {
    var json =
        assertThat(mvc.get().uri("/eli/treaty/tfeu_2016").accept(MediaTypes.HAL_JSON))
            .hasStatusOk()
            .bodyJson();

    json.extractingPath("$.celex").isEqualTo("12016E/TXT");
    json.extractingPath("$._links.self.href").isEqualTo("http://localhost/eli/treaty/tfeu_2016");
    json.extractingPath("$._links.alternate[1].href")
        .isEqualTo("http://publications.europa.eu/resource/celex/12016E%2FTXT");
  }

  @Test
  void anEliWithItsNumberOfSeveralActsAndOneOfTheOjCSeriesRoute() {
    assertThat(mvc.get().uri("/eli/dec/2007/44(1)").accept(MediaTypes.HAL_JSON)).hasStatus(404);
    assertThat(mvc.get().uri("/eli/C/2023/1464").accept(MediaTypes.HAL_JSON)).hasStatus(404);
  }

  @Test
  void anEliNoActHasIsNotFoundAsAProblem() {
    var response = mvc.get().uri("/eli/reg/2099/1").accept(MediaTypes.HAL_JSON).exchange();

    assertThat(response).hasStatus(404).hasContentType("application/problem+json");
    assertThat(response)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("No act has the ELI reg/2099/1");
  }

  @Test
  void cellarFailingIsABadGatewayOfItsOwnProblemType() {
    for (var eli : List.of(UNUSABLE, UNREACHABLE)) {
      var response = mvc.get().uri("/eli/" + eli).accept(MediaTypes.HAL_JSON).exchange();

      assertThat(response).hasStatus(502).hasContentType("application/problem+json");
      var json = assertThat(response).bodyJson();
      json.extractingPath("$.type")
          .isEqualTo("https://kludgeworks.groknull.dev/problems/upstream-unavailable");
      json.extractingPath("$.detail").asString().doesNotContain("several acts", "refused");
    }
  }

  @Test
  void aPathOfNoEliShapeIsNotFound() {
    assertThat(mvc.get().uri("/eli/reg/15/1998").accept(MediaTypes.HAL_JSON)).hasStatus(404);
    assertThat(mvc.get().uri("/eli/treaty/TFEU").accept(MediaTypes.HAL_JSON)).hasStatus(404);
  }
}
