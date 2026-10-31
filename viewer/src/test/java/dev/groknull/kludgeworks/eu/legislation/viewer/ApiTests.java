// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The API as a client uses it: from the root, by link relations, never a URL of its own making
 * (URLs and API). The whole app, on Valkey in a container, its acts held in memory (OneTreaty).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OneTreaty.class})
class ApiTests {
  @Autowired MockMvcTester mvc;

  @Test
  void anActIsFoundFromTheRootByItsCelexAndItsVersionsFollowed() {
    var root = assertThat(mvc.get().uri("/api").accept(MediaTypes.HAL_JSON)).hasStatusOk();
    root.bodyJson().extractingPath("$._links.curies[0].name").isEqualTo("av");
    var template = root.bodyJson().extractingPath("$._links['av:act'].href").asString().actual();

    var href = UriTemplate.of(template).expand(Map.of("act", "12016E/TXT")).toString();
    var act = assertThat(mvc.get().uri(URI.create(href)).accept(MediaTypes.HAL_JSON)).hasStatusOk();

    assertThat(href).isEqualTo("http://localhost/api/acts/12016E%2FTXT");
    act.bodyJson().extractingPath("$.celex").isEqualTo("12016E/TXT");
    act.bodyJson().extractingPath("$._links.self.href").isEqualTo(href);
    var latest = act.bodyJson().extractingPath("$._links.latest-version.href").asString().actual();
    assertThat(mvc.get().uri(URI.create(latest)).accept(MediaTypes.HAL_JSON))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$._links.version-history.href")
        .isEqualTo(href);
  }

  @Test
  void anActIsFoundFromTheRootByItsEli() {
    var template =
        assertThat(mvc.get().uri("/api").accept(MediaTypes.HAL_JSON))
            .bodyJson()
            .extractingPath("$._links['av:act-by-eli'].href")
            .asString()
            .actual();

    var href = UriTemplate.of(template).expand(Map.of("eli", "treaty/tfeu_2016")).toString();

    assertThat(mvc.get().uri(URI.create(href)).accept(MediaTypes.HAL_JSON))
        .hasStatus(HttpStatus.SEE_OTHER)
        .hasHeader("Location", "http://localhost/api/acts/12016E%2FTXT");
  }
}
