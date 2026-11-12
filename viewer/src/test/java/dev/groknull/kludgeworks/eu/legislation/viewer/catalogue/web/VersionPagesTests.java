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
 * Tests a version page as a reader's browser loads it, on an empty store. The preparing page comes
 * at once. The act is prepared in the background while the page listens with htmx and server-sent
 * events. Then the version page takes its place, with its outline and the text. The text is
 * transcluded from the content module. The data is the converter's fixtures, with the consolidation
 * of 2015/1998 of 22 March 2026 in English. Today is 1 June 2021. {@code VersionControllerTests}
 * tests where the addresses lead, what they refuse and the chrome.
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
    // This is the preparing page, unless another test prepared the act first.
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
      // Without JavaScript, the page reloads from a meta tag in the head. The tag is never in
      // what a boosted request swaps in.
      assertThat(first.head().html()).contains("<meta http-equiv=\"refresh\" content=\"5\">");
      assertThat(first.select("#page noscript")).isEmpty();
      assertThat(first.select("#timeline .tick, #timeline .merged"))
          .isNotEmpty()
          .noneMatch(mark -> mark.is("a"));
    }

    var arrived =
        await()
            .atMost(Duration.ofMinutes(1))
            // Poll at once. Otherwise Awaitility first waits an interval, even when the act is
            // already prepared.
            .pollDelay(Duration.ZERO)
            .pollInterval(Duration.ofMillis(250))
            .until(
                () -> page(PAGE + "?prepared=true"),
                page -> page.selectFirst(".content[hx-get^=/content/]") != null);
    // On arrival from the preparing page, the content fades in. There is no receipt.
    assertThat(arrived.select("#page.arrived")).isNotEmpty();
    assertThat(arrived.select(".receipt")).isEmpty();
    assertThat(page(PAGE).select("#page.arrived")).isEmpty();
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

    // The original. The fixtures have no Formex of it.
    var page = page("/eli/reg_impl/2015/1998/2015-11-30/eng");

    var failure = Objects.requireNonNull(page.selectFirst("#text .failure"));
    assertThat(failure.select("h2").text()).isEqualTo("Not available in English");
    // A retry cannot help, so there is no retry form.
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
      // The stream says how the act stands until it is prepared. It says nothing if another test
      // prepared the act first. Each line of a message is a data line. Then the done event comes.
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

  /** Waits until the act is prepared and its page shows the text. */
  private void prepared() {
    await()
        .atMost(Duration.ofMinutes(1))
        // Poll at once. Otherwise Awaitility first waits an interval, even when the act is already
        // prepared.
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(250))
        .until(() -> page(PAGE), page -> page.getElementById("text") != null);
  }

  /**
   * Sets today for the pages to 1 June 2021. On that day the consolidation of 15 March 2021
   * applies.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class OnTheFirstOfJune2021 {
    @Bean
    @Primary
    @SuppressWarnings("TimeZoneUsage") // a fixed stand-in for the app's Clock bean
    Clock firstOfJune2021() {
      return Clock.fixed(Instant.parse("2021-06-01T08:00:00Z"), ZoneOffset.UTC);
    }
  }
}
