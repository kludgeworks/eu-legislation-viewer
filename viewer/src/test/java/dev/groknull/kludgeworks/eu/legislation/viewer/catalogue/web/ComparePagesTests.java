// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.net.URI;
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
 * A comparison's page as a reader's browser loads it, on an empty store: the page at once, both
 * texts built in the background while its changes' part polls (htmx), then its Changes view, from
 * beside the page, each difference with the act that made it. On the converter's fixtures:
 * 2015/1998's consolidations of 22 March and 1 October 2026, in English. Where its addresses lead
 * and what it refuses, and its site bar, are CompareControllerTests'.
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
    assertThat(changes.select("nav.views .view").eachText())
        .containsExactly("Changes", "Whole text", "Synopsis");
    assertThat(changes.select("nav.views a.view").eachAttr("href"))
        .containsExactly(PAGE, PAGE + "&view=whole", PAGE + "&view=synopsis");
    assertThat(changes.select("nav.views [aria-current=page]").text()).isEqualTo("Changes");
    assertThat(changes.select("section.difference").eachAttr("id"))
        .containsExactly(
            "difference-1",
            "difference-2",
            "difference-3",
            "difference-4",
            "difference-5",
            "difference-6");
    var c = Objects.requireNonNull(changes.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    // an item of its list, as Read lays it out
    assertThat(Objects.requireNonNull(c.parent()).classNames()).contains("list");
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
    // every unit of the later text once: a difference, an opening, or folded
    assertThat(changes.select("details.fold summary").eachText())
        .containsExactly(
            "Articles 1 to 3 — no differences · show",
            "Points 1. to 8.1.2.2 (429 provisions) — no differences · show",
            "Points (a) and (b) — no differences · show",
            "Point (d) — no differences · show",
            "Point (f) — no differences · show",
            "Points 8.1.2.4 to 9.1.2.2 — no differences · show",
            "Points (a) and (b) — no differences · show",
            "Point (d) — no differences · show",
            "Point (f) — no differences · show",
            "Point 9.1.2.4 to attachment 12-P — no differences · show");
    // the annex's, 8.1.2.3's and 9.1.2.3's
    assertThat(changes.select(".lead")).hasSize(3);
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
  void countsTheDifferencesEachActMadeAndLimitsTheComparisonToOnesChanges() {
    var acts = Objects.requireNonNull(prepared().getElementById("acts"));
    var counted =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(acts.attr("hx-get")).retrieve().body(String.class)));
    var chip = Objects.requireNonNull(counted.selectFirst("#acts a.chip"));
    assertThat(chip.select(".label").text()).isEqualTo("2026/449");
    assertThat(chip.select(".count").text()).isEqualTo("6");
    assertThat(chip.attr("href")).isEqualTo(PAGE + "&act=32026R0449");

    var only =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(chip.attr("href")).retrieve().body(String.class)));
    var ready = Objects.requireNonNull(only.getElementById("text"));
    assertThat(ready.attr("hx-get"))
        .isEqualTo(ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01&act=32026R0449");
    var changes =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(ready.attr("hx-get")).retrieve().body(String.class)));
    assertThat(changes.select(".controls .only").text())
        .isEqualTo("Only 2026/449’s changes · show all acts");
    assertThat(changes.select(".controls .only a").attr("href")).isEqualTo(PAGE);
    assertThat(changes.select("section.difference")).hasSize(6);
  }

  @Test
  void showsTheWholeLaterTextWithItsDifferencesMarkedInPlace() {
    prepared();
    var page =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(PAGE + "&view=whole").retrieve().body(String.class)));
    var ready = Objects.requireNonNull(page.getElementById("text"));
    assertThat(ready.attr("hx-get"))
        .isEqualTo(ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01&view=whole");
    var whole =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(ready.attr("hx-get")).retrieve().body(String.class)));
    assertThat(whole.select("nav.views [aria-current=page]").text()).isEqualTo("Whole text");
    assertThat(whole.select(".controls b").text()).isEqualTo("6 differences");
    assertThat(whole.select("section.difference").eachAttr("id"))
        .containsExactly(
            "difference-1",
            "difference-2",
            "difference-3",
            "difference-4",
            "difference-5",
            "difference-6");
    // the provisions the same in both, as they read, not folded
    assertThat(whole.select("details.fold")).isEmpty();
    var a = Objects.requireNonNull(whole.getElementById("anx/pnt_8.1.2.3/pnt_a"));
    assertThat(a.closest("section.difference")).isNull();
    var c = Objects.requireNonNull(whole.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(c.parent()).isNotNull();
    assertThat(Objects.requireNonNull(c.parent()).id()).isEqualTo("difference-1");
    assertThat(whole.select("#difference-1 .sidenote .made").text())
        .isEqualTo("Replaced by 2026/449");
    assertThat(whole.select("nav.pager a")).hasSize(6);
  }

  @Test
  void alignsTheTwoTextsByProvisionInTheSynopsis() {
    prepared();
    var page =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(PAGE + "&view=synopsis").retrieve().body(String.class)));
    var synopsis =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get()
                    .uri(Objects.requireNonNull(page.getElementById("text")).attr("hx-get"))
                    .retrieve()
                    .body(String.class)));
    assertThat(synopsis.select("nav.views [aria-current=page]").text()).isEqualTo("Synopsis");
    assertThat(synopsis.select(".ledger-head .celex").eachText())
        .containsExactly("02015R1998-20260322", "02015R1998-20261001");
    assertThat(synopsis.select(".ledger-row[id^=difference-]")).hasSize(6);
    var first = Objects.requireNonNull(synopsis.getElementById("difference-1"));
    var provision = Objects.requireNonNull(first.selectFirst(".provision"));
    assertThat(provision.ownText()).isEqualTo("8.1.2.3(c)");
    assertThat(provision.select(".n").text()).isEqualTo("difference 1 of 6");
    // the same marked unit in both, its insertions hidden in From by the page's style
    assertThat(first.select(".from ins.diff").text())
        .isEqualTo(first.select(".to ins.diff").text())
        .isNotEmpty();
    assertThat(first.select(".what .made").text()).isEqualTo("Replaced by 2026/449");
    assertThat(first.select(".what .steps a").eachAttr("href")).containsExactly("#difference-2");
    // its neighbours, the same in both, with what last made them as they are
    // those between the first and last of a run of more than three, folded
    assertThat(synopsis.select(".ledger-fold > summary").eachText())
        .containsExactly(
            "Points 1.0 to 8.1.2.1 (427 provisions) — no differences · show",
            "Points 8.1.3 to 9.1.2.1 — no differences · show",
            "Point 9.1.3 to attachment 12-O — no differences · show");
    // a fold opens in place: its units' rows, each the same in both, with its history
    var fold = Objects.requireNonNull(synopsis.selectFirst(".ledger-fold"));
    var rows =
        Jsoup.parseBodyFragment(
            Objects.requireNonNull(
                http.get().uri(URI.create(fold.attr("hx-get"))).retrieve().body(String.class)));
    // the run's units in the text's order, an attachment's parts too
    assertThat(rows.select(".ledger-same .provision").eachText())
        .startsWith("1.0", "1.0.1")
        .endsWith("8.1.2.1");
    var opened = Objects.requireNonNull(rows.selectFirst(".ledger-same"));
    assertThat(opened.select(".from").text()).isEqualTo(opened.select(".to").text()).isNotBlank();
    assertThat(opened.select(".history").text()).isNotBlank();
    var same = synopsis.select(".ledger-same");
    assertThat(same.select(".provision").eachText())
        .containsSubsequence("8.1.2.2", "8.1.2.3(a)", "8.1.2.3(b)", "8.1.2.3(d)", "8.1.2.3(f)");
    var a =
        same.stream()
            .filter(row -> row.select(".provision").text().equals("8.1.2.3(a)"))
            .findFirst()
            .orElseThrow();
    assertThat(a.select(".from").text()).isEqualTo(a.select(".to").text());
    assertThat(a.select(".history").text())
        .isEqualTo(
            "As in the original act · applies from 1 February 2016 · Article 2 of 2015/1998");
  }

  @Test
  void opensAFoldWithItsUnitsFromTheText() {
    var ready = Objects.requireNonNull(prepared().getElementById("text"));
    var changes =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(ready.attr("hx-get")).retrieve().body(String.class)));
    var fold =
        Objects.requireNonNull(changes.selectFirst("details.fold:contains(Points (a) and (b))"));
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

  /** The page, once the act is prepared: its changes loading from beside it. */
  private Document prepared() {
    return await()
        .atMost(Duration.ofMinutes(1))
        // at once: Awaitility otherwise first waits an interval, even for an act prepared already
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(250))
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
