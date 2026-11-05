// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import org.springframework.web.client.RestClient;

/**
 * The home page as a reader's browser loads it: the Find an act box, a CELEX number or ELI going
 * straight to the act, title words listing the acts that have them, the site bar's way back to
 * them. Cellar is OneActCellar's: 2015/1998, and 2026/449 amending it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class HomePagesTests {
  private static final String COMPARE = "/eli/reg_impl/2015/1998/eng/compare";

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();
  }

  @Test
  void hasTheFindAnActBoxAndNoOtherInTheSiteBar() {
    var page = page("/");

    assertThat(page.select("form.find-act input[name=q]").attr("placeholder"))
        .isEqualTo("Title words, CELEX number or ELI");
    assertThat(page.select("nav.site form.find")).isEmpty();
    assertThat(page.select(".results")).isEmpty();
    assertThat(page.select(".about h3").eachText())
        .containsExactly("What this is", "What it adds", "What it isn’t");
  }

  @Test
  void goesStraightToTheActForItsCelexNumber() {
    assertThat(location("/?q=32015r1998")).isEqualTo(URI.create(COMPARE));
  }

  @Test
  void goesStraightToTheActForItsEli() {
    assertThat(location("/?q=http://data.europa.eu/eli/reg_impl/2015/1998/oj"))
        .isEqualTo(URI.create(COMPARE));
    assertThat(location("/?q=eli/reg_impl/2015/1998")).isEqualTo(URI.create(COMPARE));
  }

  @Test
  void saysNoActHasACelexNumberCellarDoesntKnow() {
    // 185/2010: OneActCellar knows 2015/1998 alone
    assertThat(page("/?q=32010R0185").select(".none").text())
        .isEqualTo("No act has the CELEX number or ELI 32010R0185.");
  }

  @Test
  void listsTheActsWithTheWordsInTheirTitle() {
    var response = http.get().uri("/?q=Aviation+security").retrieve().toEntity(String.class);
    var page = Jsoup.parse(Objects.requireNonNull(response.getBody()));

    assertThat(page.select(".results-head h2").text())
        .isEqualTo("Acts matching “Aviation security”");
    assertThat(page.select(".result .cx").stream().map(cx -> cx.ownText()))
        .containsExactly("32015R1998", "32026R0449");
    assertThat(page.select(".result .cx .k").eachText())
        .containsExactly("Implementing Regulation", "Amending act");
    var avsec = Objects.requireNonNull(page.selectFirst(".result .t"));
    assertThat(avsec.select("a").getFirst().text())
        .isEqualTo("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015");
    assertThat(avsec.select("p").text())
        .isEqualTo(
            "laying down detailed measures for the implementation of the common basic standards"
                + " on aviation security (Text with EEA relevance)");
    assertThat(avsec.select("a").eachAttr("href")).containsOnly(COMPARE);
    assertThat(avsec.select("a.to").text())
        .isEqualTo("Compare the latest version with the one before →");
    var amending = page.select(".result .t").get(1);
    assertThat(amending.select("a.to").text()).isEqualTo("What it changed in 2015/1998 →");
    assertThat(amending.select("a.to").attr("href")).isEqualTo(COMPARE + "?act=32026R0449");
    assertThat(page.select(".filters a").eachText())
        .containsExactly("All", "Regulations", "Directives", "Decisions", "Amending acts");
    assertThat(page.select(".filters a[aria-current=page]").text()).isEqualTo("All");
    assertThat(page.select(".filters a").get(4).attr("href"))
        .isEqualTo("/?q=Aviation+security&type=amending");
    assertThat(page.select(".more").text()).isEqualTo("Acts 1–2, closest match first");
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .singleElement()
        .asString()
        .startsWith("search=q=Aviation+security;");
  }

  @Test
  void linksBackToTheLastSearchsResultsFromTheSiteBar() {
    var page =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get()
                    .uri(COMPARE + "?from=2026-03-22&to=2026-10-01")
                    .header(HttpHeaders.COOKIE, "search=q=aviation+security&type=regulation")
                    .retrieve()
                    .body(String.class)));

    var back = Objects.requireNonNull(page.selectFirst("nav.site a.results"));
    assertThat(back.text()).isEqualTo("‹ Results for “aviation security”");
    assertThat(back.attr("href")).isEqualTo("/?q=aviation%20security&type=regulation");
    assertThat(page.select("nav.site form.find input[name=q]")).hasSize(1);
  }

  @Test
  void findsNoKindOfActThereIsnt() {
    HttpStatusCode status =
        http.get()
            .uri("/?q=aviation&type=treaty")
            .exchange((request, response) -> response.getStatusCode());
    assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private Document page(String path) {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(path).retrieve().body(String.class)));
  }

  /** Where {@code path} redirects, not followed. */
  private URI location(String path) {
    ResponseEntity<Void> response = http.get().uri(path).retrieve().toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    return Objects.requireNonNull(response.getHeaders().getLocation());
  }
}
