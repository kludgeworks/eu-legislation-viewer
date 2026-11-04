// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
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
    assertThat(article.select("#art_1 > h2 > .num").text()).isEqualTo("Article 1");
  }

  @Test
  void followsTheDeviceUntilTheReaderChoosesATheme() {
    assertThat(theme(http.get().uri(PAGE).retrieve().body(String.class))).isEqualTo("system");
    var dark = http.get().uri(PAGE).cookie("theme", "dark").retrieve().body(String.class);
    assertThat(theme(dark)).isEqualTo("dark");
    assertThat(Jsoup.parse(Objects.requireNonNull(dark)).select("form.theme [aria-pressed=true]"))
        .extracting(button -> button.attr("value"))
        .containsExactly("dark");
  }

  @Test
  void keepsTheChosenThemeAndGoesBackToThePage() {
    var chosen = choose("dark", PAGE + "?from=x");
    assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(chosen.getHeaders().getLocation()).isEqualTo(URI.create(PAGE + "?from=x"));
    assertThat(chosen.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .startsWith("theme=dark;")
        .contains("HttpOnly");
  }

  @Test
  void keepsTheThemeTheScriptSwitchedThePageTo() {
    var kept = choose("light", PAGE, "cors");
    assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(kept.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("theme=light;");
  }

  @Test
  void goesBackOnlyToAPageOfThisSite() {
    for (var elsewhere :
        new String[] {"https://example.org/", "//example.org/", "/\\example.org/"}) {
      assertThat(choose("light", elsewhere).getHeaders().getLocation())
          .as(elsewhere)
          .isEqualTo(URI.create("/"));
    }
  }

  @Test
  void findsNoVersionBeforeTheActsTextApplies() {
    assertThat(status("/eli/reg_impl/2015/1998/2000-01-01/eng")).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void findsNoPageInALanguageTheEuDoesntHave() {
    assertThat(status("/eli/reg_impl/2015/1998/2026-03-22/xxx")).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The theme switch's form posted, as a form does, its redirect not followed. */
  private ResponseEntity<Void> choose(String theme, String back) {
    return choose(theme, back, "navigate");
  }

  /** The theme switch's form posted, in the fetch mode {@code mode}. */
  private ResponseEntity<Void> choose(String theme, String back, String mode) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("set", theme);
    form.add("back", back);
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .requestFactory(
            new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
        .build()
        .post()
        .uri("/theme")
        .header("Sec-Fetch-Mode", mode)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }

  private static String theme(@Nullable String html) {
    return Jsoup.parse(Objects.requireNonNull(html)).root().child(0).attr("data-theme");
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
