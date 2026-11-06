// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * A version's page as a reader's browser loads it, on an empty store: the preparing page at once,
 * the act prepared in the background while it listens (htmx, server-sent events), then the
 * version's page in its place, its outline and the text, transcluded from the content module. On
 * the converter's fixtures (2015/1998's consolidation of 22 March 2026 in English), on 1 June 2021.
 * Where its addresses lead and what it refuses, and its chrome, are VersionControllerTests'.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class VersionPagesTests {
  private static final String PAGE = "/eli/reg_impl/2015/1998/2026-03-22/eng";
  private static final String EVENTS = "/eli/reg_impl/2015/1998/eng/prepare/events";

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void preparesTheActThenBecomesItsPageShowingTheText() {
    var first =
        Jsoup.parse(Objects.requireNonNull(http.get().uri(PAGE).retrieve().body(String.class)));
    // the preparing page, unless another test prepared the act first
    var preparing = first.selectFirst(".preparing");
    if (preparing != null) {
      assertThat(preparing.attr("hx-get")).isEqualTo(PAGE);
      assertThat(preparing.attr("hx-trigger")).isEqualTo("done");
      assertThat(preparing.attr("hx-select")).isEqualTo("#page");
      assertThat(
              Objects.requireNonNull(preparing.selectFirst("[hx-sse:connect]"))
                  .attr("hx-sse:connect"))
          .isEqualTo(EVENTS);
      assertThat(preparing.select("h2").eachText()).contains("Preparing this act");
      assertThat(first.select("nav.mode [aria-disabled]")).hasSize(2);
      // reloading without JavaScript from the head: never in what a boosted request swaps in
      assertThat(first.head().html()).contains("<meta http-equiv=\"refresh\" content=\"5\">");
      assertThat(first.select("#page noscript")).isEmpty();
      assertThat(first.select("#timeline .tick, #timeline .merged"))
          .isNotEmpty()
          .noneMatch(mark -> mark.is("a"));
    }

    var arrived =
        await()
            .atMost(Duration.ofMinutes(1))
            // at once: Awaitility otherwise first waits an interval, even for an act prepared
            // already
            .pollDelay(Duration.ZERO)
            .pollInterval(Duration.ofMillis(250))
            .until(
                () -> page(PAGE + "?prepared=true"),
                page -> page.selectFirst(".content[hx-get^=/content/]") != null);
    // arriving from the preparing page: what was prepared, above what fades in
    assertThat(Objects.requireNonNull(arrived.selectFirst(".receipt")).text())
        .startsWith("✓ Prepared · ")
        .contains(" in English");
    assertThat(page(PAGE).selectFirst(".receipt")).isNull();
    var loads = Objects.requireNonNull(arrived.selectFirst(".content"));
    assertThat(loads.attr("hx-trigger")).isEqualTo("load");
    assertThat(arrived.select("nav.outline > ul > li > :is(a, details > summary > a)"))
        .extracting(link -> link.attr("href"), Element::text)
        .startsWith(tuple("#art_1", "Article 1"), tuple("#art_2", "Article 2"));

    var html = http.get().uri(loads.attr("hx-get")).retrieve().body(String.class);
    var article = Jsoup.parse(Objects.requireNonNull(html)).selectFirst("article.legal-text");
    assertThat(article).isNotNull();
    assertThat(article.attr("lang")).isEqualTo("en");
    assertThat(article.select("#art_1 > h2 > .num").text()).isEqualTo("Article 1");
  }

  @Test
  void saysWhyAVersionsTextCouldntBeMadeInItsPlaceWithWhatCanHelp() {
    prepared();

    // the original, of which the fixtures have no Formex
    var page = page("/eli/reg_impl/2015/1998/2015-11-30/eng");

    var failure = Objects.requireNonNull(page.selectFirst("#text .failure"));
    assertThat(failure.select("h2").text()).isEqualTo("Not available in English");
    // nothing a retry can help
    assertThat(failure.select("form")).isEmpty();
    assertThat(failure.select(".failure-actions a"))
        .extracting(link -> link.attr("href"), Element::text)
        .startsWith(
            tuple("/eli/reg_impl/2015/1998/2015-11-30/fra", "Read it in French"),
            tuple(
                "/eli/reg_impl/2015/1998/2021-03-15/eng",
                "Read the version after (from 15 March 2021)"));
    assertThat(page.select("#timeline a.tick.failed[aria-current]")).hasSize(1);
  }

  @Test
  void streamsHowTheActStandsUntilItIsPrepared() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + EVENTS))
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofMinutes(2))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofLines());
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
      // how it stands as long as it isn't prepared (nothing, if another test prepared it first),
      // each message's lines a data line each, then done
      var lines = response.body().toList();
      assertThat(lines).contains("event:done");
      var stands =
          String.join(
              "\n",
              lines.subList(0, lines.indexOf("event:done")).stream()
                  .filter(line -> line.startsWith("data:"))
                  .toList());
      if (!stands.isBlank()) {
        assertThat(stands)
            .contains("<hx-partial hx-target=\"#timeline\"", "<hx-partial hx-target=\"#progress\"")
            .containsAnyOf("versions ready", "Waiting its turn");
      }
      assertThat(page(PAGE).selectFirst(".outlined")).isNotNull();
    }
  }

  @Test
  void givesABoostedLinkTheWholePage() {
    prepared();
    var boosted =
        http.get()
            .uri(PAGE)
            .header("HX-Request", "true")
            .header("HX-Boosted", "true")
            .retrieve()
            .body(String.class);
    var page = Jsoup.parse(Objects.requireNonNull(boosted)).getElementById("page");
    assertThat(page).isNotNull();
    assertThat(page.select("header.mast, section.timeline, #text")).hasSize(3);
  }

  private Document page(String uri) {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(uri).retrieve().body(String.class)));
  }

  /** Once the act is prepared: its page shows the text. */
  private void prepared() {
    await()
        .atMost(Duration.ofMinutes(1))
        // at once: Awaitility otherwise first waits an interval, even for an act prepared already
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(250))
        .until(() -> page(PAGE), page -> page.getElementById("text") != null);
  }

  /** Today, for the pages: 1 June 2021, when the consolidation of 15 March 2021 applies. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OnTheFirstOfJune2021 {
    @Bean
    @Primary
    @SuppressWarnings("TimeZoneUsage") // the app's Clock bean, fixed
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
