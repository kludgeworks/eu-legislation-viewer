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
 * Tests a comparison page as a reader's browser loads it, on an empty store. The page comes at
 * once. Both texts are built in the background while the changes part of the page polls with htmx.
 * Then the Changes view loads from beside the page, with the act that made each difference. The
 * data is the converter's fixtures: the consolidations of 2015/1998 of 22 March and 1 October 2026,
 * in English. {@code CompareControllerTests} tests where the addresses lead, what they refuse and
 * the site bar.
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
    // This is the preparing page, unless another test prepared the act first.
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
    // It is an item of its list, as the Read mode lays it out.
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
    // Each unit of the later text shows once: as a difference, as an opening, or in a fold.
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
    // The leads of the annex, 8.1.2.3 and 9.1.2.3.
    assertThat(changes.select(".lead")).hasSize(3);
    // There is no pager. The head of the outline follows the reader.
    assertThat(changes.select("nav.pager")).isEmpty();
    assertThat(changes.select(".outline-follow").attr("data-total")).isEqualTo("6");
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
    // The provisions that are the same in both texts show as they read. They are not folded.
    assertThat(whole.select("details.fold")).isEmpty();
    var a = Objects.requireNonNull(whole.getElementById("anx/pnt_8.1.2.3/pnt_a"));
    assertThat(a.closest("section.difference")).isNull();
    var c = Objects.requireNonNull(whole.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(c.parent()).isNotNull();
    assertThat(Objects.requireNonNull(c.parent()).id()).isEqualTo("difference-1");
    assertThat(whole.select("#difference-1 .sidenote .made").text())
        .isEqualTo("Replaced by 2026/449");
    assertThat(whole.select(".outline-follow .where").text()).isEqualTo("6 differences");
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
    // Both sides have the same marked unit. In From, the style of the page hides its
    // insertions.
    assertThat(first.select(".from ins.diff").text())
        .isEqualTo(first.select(".to ins.diff").text())
        .isNotEmpty();
    assertThat(first.select(".what .made").text()).isEqualTo("Replaced by 2026/449");
    assertThat(first.select(".what .steps a").eachAttr("href")).containsExactly("#difference-2");
    // Its neighbours are the same in both texts. Each shows what last made it as it is. In a run
    // of more than three, the units between the first and the last are folded.
    assertThat(synopsis.select(".ledger-fold > summary").eachText())
        .containsExactly(
            "Points 1.0 to 8.1.2.1 (427 provisions) — no differences · show",
            "Points 8.1.3 to 9.1.2.1 — no differences · show",
            "Point 9.1.3 to attachment 12-O — no differences · show");
    // A fold opens in place. It shows a row for each of its units, the same in both texts, with
    // the history of the unit.
    var fold = Objects.requireNonNull(synopsis.selectFirst(".ledger-fold"));
    var rows =
        Jsoup.parseBodyFragment(
            Objects.requireNonNull(
                http.get().uri(URI.create(fold.attr("hx-get"))).retrieve().body(String.class)));
    // The units of the run are in the order of the text. This includes the parts of an attachment.
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

    // From the original. The fixtures have no Formex of it.
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
  void saysWhatThePageChoseUnderTheCardsAndWhereBothEndsAreOneVersion() {
    prepared();

    var outside = get(PAGE + "&outside=32019R0103");
    assertThat(outside.select(".compare-note").text())
        .isEqualTo("2019/103 isn’t between these two versions; showing every act’s changes.");
    assertThat(get(PAGE + "&opened=current").select(".compare-note").text())
        .startsWith("Showing the most recent amendment in force");

    var same = get(ACT + "eng/compare?from=2026-10-01&to=2026-10-01");
    assertThat(same.select(".same-version p").eachText())
        .first()
        .isEqualTo("Both ends are the same version (02015R1998-20261001, from 1 October 2026).");
    assertThat(same.select(".same-version a").eachAttr("href"))
        .containsExactly(PAGE, ACT + "2026-10-01/eng");
  }

  private Document get(String uri) {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(uri).retrieve().body(String.class)));
  }

  /** The page when the act is prepared. Its changes load from beside it. */
  private Document prepared() {
    return await()
        .atMost(Duration.ofMinutes(1))
        // Poll at once. Otherwise Awaitility first waits an interval, even when the act is already
        // prepared.
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
