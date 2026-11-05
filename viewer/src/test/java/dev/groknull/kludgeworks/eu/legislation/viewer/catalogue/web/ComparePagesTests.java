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
 * texts built in the background while its changes' part polls (htmx), then the comparison. On the
 * converter's fixtures: 2015/1998's consolidations of 22 March and 1 October 2026, in English.
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
  void comparesTheVersionsOnceBothAreBuilt() {
    var page =
        Jsoup.parse(Objects.requireNonNull(http.get().uri(PAGE).retrieve().body(String.class)));
    assertThat(page.select("nav.mode a").eachAttr("href")).containsExactly(ACT + "2026-10-01/eng");
    assertThat(page.select(".tag").eachText()).containsExactly("FROM", "TO");
    assertThat(page.select(".card-celex").eachText())
        .containsExactly("02015R1998-20260322", "02015R1998-20261001");
    assertThat(page.select(".between .chip").eachText()).containsExactly("2026/449");
    var waiting = Objects.requireNonNull(page.getElementById("text"));
    assertThat(waiting.attr("hx-get")).isEqualTo(PAGE);

    var compared =
        await()
            .atMost(Duration.ofMinutes(1))
            .pollInterval(Duration.ofSeconds(1))
            .until(this::poll, changes -> changes.selectFirst(".controls") != null);
    assertThat(compared.select(".controls b").text()).isEqualTo("6 differences");
    assertThat(compared.select("ol.differences > li")).hasSize(6);
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

  /** The page's changes, as htmx asks for them. */
  private Element poll() {
    var html = http.get().uri(PAGE).header("HX-Request", "true").retrieve().body(String.class);
    return Objects.requireNonNull(Jsoup.parse(Objects.requireNonNull(html)).getElementById("text"));
  }
}
