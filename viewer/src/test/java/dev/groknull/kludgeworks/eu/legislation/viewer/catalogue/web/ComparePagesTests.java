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
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * A comparison's page as a reader's browser loads it, on an empty store: the page at once, both
 * texts built in the background while its changes' part polls (htmx), then its Changes view, from
 * beside the page, each difference with the act that made it. On the converter's fixtures:
 * 2015/1998's consolidations of 22 March and 1 October 2026, in English.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class ComparePagesTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String PAGE = ACT + "eng/compare?from=2026-03-22&to=2026-10-01";

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void comparesTheVersionsOnceTheActIsPrepared() {
    var first =
        Jsoup.parse(Objects.requireNonNull(http.get().uri(PAGE).retrieve().body(String.class)));
    // the preparing page, unless another test prepared the act first
    var preparing = first.selectFirst(".preparing");
    if (preparing != null) {
      assertThat(preparing.attr("hx-get")).isEqualTo(PAGE);
    }

    var page = prepared();
    assertThat(page.select("nav.mode a").eachAttr("href")).containsExactly(ACT + "2026-10-01/eng");
    assertThat(page.select(".tag").eachText()).containsExactly("FROM", "TO");
    assertThat(page.select(".card-celex").eachText())
        .containsExactly("02015R1998-20260322", "02015R1998-20261001");
    assertThat(page.select(".between .chip").eachText()).containsExactly("2026/449");
    var ready = Objects.requireNonNull(page.getElementById("text"));
    var changes =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(ready.attr("hx-get")).retrieve().body(String.class)));
    assertThat(changes.select(".controls b").text()).isEqualTo("6 differences");
    assertThat(changes.select("section.difference").eachAttr("id"))
        .containsExactly(
            "difference-1",
            "difference-2",
            "difference-3",
            "difference-4",
            "difference-5",
            "difference-6");
    var c = Objects.requireNonNull(changes.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(c.select("ins.diff").text())
        .isEqualTo(
            "applied in accordance with Attachment 6-J to the Annex to Implementing Decision"
                + " C(2015) 8005");
    var sidenote = Objects.requireNonNull(changes.selectFirst("#difference-1 .sidenote"));
    assertThat(sidenote.select(".made").text()).isEqualTo("Replaced by 2026/449");
    assertThat(sidenote.select(".applies").text())
        .isEqualTo(
            "2026/449 applies in stages: from 22 March 2026, its entry into force; in part from 1"
                + " October 2026 · Article 2 of 2026/449 says which part from when");
    assertThat(sidenote.select(".applies a").attr("href"))
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32026R0449");
    assertThat(sidenote.select(".before").text()).isEqualTo("Before: original act");
    assertThat(changes.select("details.fold summary").eachText())
        .startsWith(
            "Points (a) and (b) — no differences · show",
            "Point (d) — no differences · show",
            "Point (f) — no differences · show");
    assertThat(changes.select(".lead")).hasSize(2);
    assertThat(changes.select("nav.pager a")).hasSize(6);
    var screening =
        changes.select("nav.outline a.has").stream()
            .filter(a -> a.text().startsWith("8.1.2 Screening"))
            .findFirst()
            .orElseThrow();
    assertThat(screening.select(".n").text()).isEqualTo("3");
    assertThat(screening.attr("href")).isEqualTo("#difference-1");
    assertThat(changes.select("nav.outline .chips a").eachAttr("href"))
        .containsExactly(
            "#difference-1",
            "#difference-2",
            "#difference-3",
            "#difference-4",
            "#difference-5",
            "#difference-6");
  }

  @Test
  void opensAFoldWithItsUnitsFromTheText() {
    var ready = Objects.requireNonNull(prepared().getElementById("text"));
    var changes =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(ready.attr("hx-get")).retrieve().body(String.class)));
    var fold = Objects.requireNonNull(changes.selectFirst("details.fold"));
    var units =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(URI.create(fold.attr("hx-get"))).retrieve().body(String.class)));
    assertThat(units.select("div.unit").eachAttr("id"))
        .containsExactly("anx/pnt_8.1.2.3/pnt_a", "anx/pnt_8.1.2.3/pnt_b");
  }

  @Test
  void saysWhyAVersionCantBeComparedInPlaceOfTheChanges() {
    prepared();

    // from the original, of which the fixtures have no Formex
    var page =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get()
                    .uri(ACT + "eng/compare?from=2015-11-30&to=2021-03-15")
                    .retrieve()
                    .body(String.class)));

    assertThat(page.select("#text .failure h2").text()).isEqualTo("Not available in English");
  }

  @Test
  void opensOnTheLatestVersionAgainstTheOneBefore() {
    assertThat(location(ACT + "eng/compare"))
        .isEqualTo(URI.create(ACT + "eng/compare?from=2026-03-22&to=2026-10-01"));
  }

  @Test
  void goesToTheVersionsTheDatesAskForEarlierFirst() {
    assertThat(location(ACT + "eng/compare?from=2026-12-01&to=2021-06-01"))
        .isEqualTo(URI.create(ACT + "eng/compare?from=2021-03-15&to=2026-10-01"));
  }

  @Test
  void findsNoVersionBeforeTheActsTextApplies() {
    var status =
        http.get()
            .uri(ACT + "eng/compare?from=2000-01-01&to=2026-10-01")
            .header(HttpHeaders.ACCEPT, "text/html")
            .exchange((request, response) -> response.getStatusCode());
    assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Where {@code path} redirects, not followed. */
  private URI location(String path) {
    var response =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build()
            .get()
            .uri(path)
            .retrieve()
            .toBodilessEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    return Objects.requireNonNull(response.getHeaders().getLocation());
  }

  /** The page, once the act is prepared: its changes loading from beside it. */
  private Document prepared() {
    return await()
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            this::page,
            page -> {
              var text = page.getElementById("text");
              return text != null
                  && text.attr("hx-get")
                      .equals(ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01");
            });
  }

  private Document page() {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(PAGE).retrieve().body(String.class)));
  }
}
