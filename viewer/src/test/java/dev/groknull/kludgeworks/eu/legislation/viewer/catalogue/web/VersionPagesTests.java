// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.time.Duration;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * A version's page as a reader's browser loads it, on an empty store: the page at once, its text
 * built in the background while the page polls (htmx), then transcluded from the content module. On
 * the converter's fixtures (2015/1998's consolidation of 22 March 2026 in English).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class VersionPagesTests {
  private static final String PAGE = "/eli/reg_impl/2015/1998/2026-03-22/eng";

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void showsTheTextOnceItIsBuilt() {
    var page = http.get().uri(PAGE).retrieve().toEntity(String.class);
    assertThat(page.getHeaders().getVary()).contains("HX-Request");
    var waiting = text(Objects.requireNonNull(page.getBody()));
    assertThat(waiting.attr("hx-get")).isEqualTo(PAGE);
    assertThat(waiting.attr("hx-trigger")).isEqualTo("load delay:1s");

    var ready =
        await()
            .atMost(Duration.ofMinutes(1))
            .pollInterval(Duration.ofSeconds(1))
            .until(this::poll, text -> text.attr("hx-get").startsWith("/content/"));
    assertThat(ready.attr("hx-trigger")).isEqualTo("load");

    var html = http.get().uri(ready.attr("hx-get")).retrieve().body(String.class);
    var article = Jsoup.parse(Objects.requireNonNull(html)).selectFirst("article.legal-text");
    assertThat(article).isNotNull();
    assertThat(article.attr("lang")).isEqualTo("en");
    assertThat(article.select("#art_1 > .num").text()).isEqualTo("Article 1");
  }

  @Test
  void findsNoVersionBeforeTheActsTextApplies() {
    assertThat(status("/eli/reg_impl/2015/1998/2000-01-01/eng")).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void findsNoPageInALanguageTheEuDoesntHave() {
    assertThat(status("/eli/reg_impl/2015/1998/2026-03-22/xxx")).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The page's text, as htmx asks for it. */
  private Element poll() {
    return text(
        Objects.requireNonNull(
            http.get().uri(PAGE).header("HX-Request", "true").retrieve().body(String.class)));
  }

  private static Element text(String html) {
    return Objects.requireNonNull(Jsoup.parse(html).getElementById("text"));
  }

  private HttpStatusCode status(String path) {
    return http.get()
        .uri(path)
        .header(HttpHeaders.ACCEPT, "text/html")
        .exchange((request, response) -> response.getStatusCode());
  }
}
