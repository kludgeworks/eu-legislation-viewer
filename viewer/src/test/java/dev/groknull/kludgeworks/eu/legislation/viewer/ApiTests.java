// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.UriTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The API as a client uses it: from the root, by link relations, never a URL of its own making
 * (URLs and API). The whole app, on Valkey in a container, its acts held in memory.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ApiTests.InMemory.class})
class ApiTests {
  static final ActIdentifier TFEU = new ActIdentifier("12016E/TXT");

  @TestConfiguration
  static class InMemory {
    @Bean
    @Primary
    Acts inMemoryActs() {
      var tfeu =
          new Act(
              TFEU,
              Eli.treaty("tfeu_2016"),
              new ResourceType("TREATY"),
              LocalDate.of(2016, 6, 7),
              true,
              null,
              new Timeline(List.of(Version.original(LocalDate.of(2016, 6, 7), TFEU))));
      return new Acts() {
        @Override
        public Optional<Act> find(ActIdentifier id) {
          return Optional.of(tfeu).filter(a -> a.id().equals(id));
        }

        @Override
        public Optional<Act> find(Eli eli) {
          return Optional.of(tfeu).filter(a -> eli.equals(a.eli()));
        }
      };
    }
  }

  @Autowired MockMvcTester mvc;

  @Test
  void anActIsFoundFromTheRootByItsEli() {
    var root = assertThat(mvc.get().uri("/api").accept(MediaTypes.HAL_JSON)).hasStatusOk();
    root.bodyJson().extractingPath("$._links.curies[0].name").isEqualTo("av");
    var template = root.bodyJson().extractingPath("$._links['av:act'].href").asString().actual();

    var href = UriTemplate.of(template).expand(Map.of("eli", "treaty/tfeu_2016")).toString();
    var act = assertThat(mvc.get().uri(href).accept(MediaTypes.HAL_JSON)).hasStatusOk().bodyJson();

    act.extractingPath("$.celex").isEqualTo("12016E/TXT");
    act.extractingPath("$._links.self.href").isEqualTo(href);
    act.extractingPath("$._embedded['av:versions'][0].kind").isEqualTo("ORIGINAL");
  }
}
