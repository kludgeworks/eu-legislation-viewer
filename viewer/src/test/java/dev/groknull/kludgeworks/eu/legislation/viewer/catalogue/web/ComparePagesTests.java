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
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * Tests the compare page as a reader's browser loads it, on an empty store. The page comes at once.
 * Both texts are built in the background while the page shows their preparation. Then the page is
 * the act's page in Compare mode, with two slots that the comparison module fills: the acts between
 * the versions and the changes. These tests check the page and where its slots load from. The
 * comparison module's tests check what loads there. The data is the converter's fixtures: the
 * consolidations of 2015/1998 of 22 March and 1 October 2026, in English. {@code
 * CompareControllerTests} tests where the addresses lead, what they refuse and the site bar.
 */
// Bind to 127.0.0.1, where the test client calls. On the wildcard address, the port that the
// system picks can be one that another program, such as an IDE, holds on 127.0.0.1.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
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
  void showsTheTwoDaysAndLoadsTheComparisonIntoItsSlotOnceTheActIsPrepared() {
    var first = get(PAGE);
    // This is the preparing page, unless another test prepared the act first.
    var preparing = first.selectFirst(".preparing");
    if (preparing != null) {
      assertThat(preparing.attr("hx-get")).isEqualTo(PAGE);
    }

    var page = prepared(PAGE, ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01");
    assertThat(page.select("nav.mode a").eachAttr("href"))
        .containsExactly(ACT + "eng/history?on=2026-10-01", ACT + "2026-10-01/eng");
    assertThat(page.select("#compare-head").text()).isEqualTo("2015/1998 on two days");
    assertThat(page.select("form.period input[type=date]").eachAttr("value"))
        .containsExactly("2026-03-22", "2026-10-01");
    assertThat(page.select("section.compare p").eachText().getFirst())
        .startsWith("From 22 March 2026 to 1 October 2026.")
        .contains("between these days");
    // The page shows days only: no version, no timeline, no act filter.
    assertThat(page.select(".timeline, .card-celex, #acts")).isEmpty();
    var text = Objects.requireNonNull(page.getElementById("text"));
    assertThat(text.attr("hx-trigger")).isEqualTo("load");
    assertThat(text.select(".loading")).hasSize(1);
  }

  @Test
  void passesTheViewAndTheDaysOnToItsSlot() {
    prepared(
        PAGE + "&view=whole", ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01&view=whole");
    // A day that is not the first day of its version goes to the slot beside that first day.
    prepared(
        ACT + "eng/compare?from=2026-03-22&to=2026-10-05",
        ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01&toDay=2026-10-05");
  }

  @Test
  void loadsTheWordingOfOneProvisionOnTheTwoDays() {
    var page =
        prepared(
            PAGE + "&unit=anx",
            ACT + "eng/compare/place?from=2026-03-22&to=2026-10-01&eId=anx&sides=true");
    assertThat(page.select("#compare-head").text()).endsWith("of 2015/1998 on two days");
    assertThat(page.select("form.period input[name=unit]").attr("value")).isEqualTo("anx");
    assertThat(page.select("section.compare a[data-view=changes]").attr("href")).isEqualTo(PAGE);
  }

  @Test
  void saysWhyAVersionCantBeComparedInPlaceOfTheChanges() {
    prepared(PAGE, ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01");

    // From the original. The fixtures have no Formex of it.
    var page = get(ACT + "eng/compare?from=2015-11-30&to=2021-03-15");

    assertThat(page.select("#text .failure h2").text()).isEqualTo("Not available in English");
  }

  @Test
  void saysThatTheWordingIsTheSameWhereTheTwoDaysHaveOneText() {
    prepared(PAGE, ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01");

    var same = get(ACT + "eng/compare?from=2026-10-01&to=2026-10-09");
    assertThat(same.select(".same-version p").eachText())
        .first()
        .isEqualTo("The wording is the same on the two days.");
    assertThat(same.select(".same-version a").eachAttr("href"))
        .containsExactly(ACT + "2026-10-09/eng");
    assertThat(same.select("section.compare p").eachText().getFirst())
        .contains("No amending act changed 2015/1998 between these days.");
  }

  private Document get(String uri) {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(uri).retrieve().body(String.class)));
  }

  /**
   * The page at {@code uri} when the act is prepared: its changes slot loads from {@code changes}.
   */
  private Document prepared(String uri, String changes) {
    return await()
        .atMost(Duration.ofMinutes(1))
        // Poll at once. Otherwise Awaitility first waits an interval, even when the act is already
        // prepared.
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(250))
        .until(
            () -> get(uri),
            page -> {
              var text = page.getElementById("text");
              return text != null && text.attr("hx-get").equals(changes);
            });
  }
}
