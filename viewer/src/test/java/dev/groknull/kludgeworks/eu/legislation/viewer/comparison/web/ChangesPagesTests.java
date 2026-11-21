// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixturesConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Tests the comparison's parts of the compare page as a reader's browser loads them, on an empty
 * store. The compare page is the catalogue's. It loads these parts into its slots from their
 * addresses, which are the page's address with a segment added. The tests open the page once, so
 * that the act is prepared, then load the parts from those addresses: the changes in each view,
 * with the act that made each difference, and the acts between the versions with their counts. The
 * data is the converter's fixtures: the consolidations of 2015/1998 of 22 March and 1 October 2026,
 * in English. {@code ChangesControllerTests} tests what the parts refuse.
 */
// Bind to 127.0.0.1, where the test client calls. On the wildcard address, the port that the
// system picks can be one that another program, such as an IDE, holds on 127.0.0.1.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@Import({TestcontainersConfiguration.class, OneActCellar.class, FormexFixturesConfiguration.class})
class ChangesPagesTests {
  private static final String ACT = "/eli/reg_impl/2015/1998/";
  private static final String PAGE = ACT + "eng/compare?from=2026-03-22&to=2026-10-01";
  private static final String CHANGES = ACT + "eng/compare/changes?from=2026-03-22&to=2026-10-01";
  private static final String DIFFERENCE =
      ACT + "eng/compare/difference?from=2026-03-22&to=2026-10-01";

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void comparesTheVersionsOnceTheActIsPrepared() {
    var changes = prepared();
    assertThat(changes.select(".controls b").text()).isEqualTo("6 provisions differ");
    // 2026/449 made them all. No corrigendum did, so the legend has no corrigendum.
    assertThat(changes.select(".controls .by").text()).isEqualTo("by 2026/449");
    assertThat(changes.select(".controls .legend").text())
        .contains("inserted")
        .doesNotContain("corrigendum");
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
            "2026/449 applies in parts: from 22 March 2026, its entry into force; in part from 1"
                + " October 2026 · Article 2 of 2026/449 ↗ (opens EUR-Lex) says which part from"
                + " when");
    assertThat(sidenote.select(".applies a").attr("href"))
        .isEqualTo("https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32026R0449");
    assertThat(sidenote.select(".before")).isEmpty();
    // Each occurrence of the later text shows once: as a difference, as an opening, or in a fold.
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
    assertThat(changes.select("nav.outline .rows a").eachAttr("href"))
        .containsExactly(
            "#difference-1",
            "#difference-2",
            "#difference-3",
            "#difference-4",
            "#difference-5",
            "#difference-6");
  }

  @Test
  void doesNotLimitTheComparisonToTheChangesOfOneAct() {
    prepared();
    // Compare has no act filter. An old address with an act shows the changes of all acts.
    var changes = get(CHANGES + "&act=32026R0449");
    assertThat(changes.select(".controls .only")).isEmpty();
    assertThat(changes.select(".controls b").text()).isEqualTo("6 provisions differ");
    assertThat(changes.select("section.difference.unknown")).isEmpty();
  }

  @Test
  void showsTheWholeLaterTextWithItsDifferencesMarkedInPlace() {
    prepared();
    var whole = get(CHANGES + "&view=whole");
    assertThat(whole.select("nav.views [aria-current=page]").text()).isEqualTo("Whole text");
    assertThat(whole.select(".controls b").text()).isEqualTo("6 provisions differ");
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
    var synopsis = get(CHANGES + "&view=synopsis");
    assertThat(synopsis.select("nav.views [aria-current=page]").text()).isEqualTo("Synopsis");
    assertThat(synopsis.select(".ledger-head .day").eachText())
        .containsExactly("the law on 22 March 2026", "the law on 1 October 2026");
    assertThat(synopsis.select(".ledger-row[id^=difference-]")).hasSize(6);
    var first = Objects.requireNonNull(synopsis.getElementById("difference-1"));
    var provision = Objects.requireNonNull(first.selectFirst(".provision"));
    assertThat(provision.ownText()).isEqualTo("8.1.2.3(c)");
    assertThat(provision.select(".n").text()).isEqualTo("difference 1 of 6");
    // Both sides have the same occurrence with its edits. In From, the style of the page hides its
    // insertions.
    assertThat(first.select(".from ins.diff").text())
        .isEqualTo(first.select(".to ins.diff").text())
        .isNotEmpty();
    assertThat(first.select(".what .made").text()).isEqualTo("Replaced by 2026/449");
    assertThat(first.select(".what .steps a").eachAttr("href")).containsExactly("#difference-2");
    // Its neighbours are the same in both texts. Each shows what last made it as it is. In a run
    // of more than three, the occurrences between the first and the last are folded.
    assertThat(synopsis.select(".ledger-fold > summary").eachText())
        .containsExactly(
            "Points 1.0 to 8.1.2.1 (427 provisions) — no differences · show",
            "Points 8.1.3 to 9.1.2.1 — no differences · show",
            "Point 9.1.3 to attachment 12-O — no differences · show");
    // A fold opens in place. It shows a row for each of its occurrences, the same in both texts,
    // with
    // the history of the occurrence.
    var fold = Objects.requireNonNull(synopsis.selectFirst(".ledger-fold"));
    var rows =
        Jsoup.parseBodyFragment(
            Objects.requireNonNull(
                http.get().uri(URI.create(fold.attr("hx-get"))).retrieve().body(String.class)));
    // The occurrences of the run are in the order of the text. This includes the parts of an
    // attachment.
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
            "As in the original act · applies from 1 February 2016 · Article 2 of 2015/1998 ↗"
                + " (opens EUR-Lex)");
  }

  @Test
  void opensAFoldWithItsOccurrencesFromTheText() {
    var changes = prepared();
    var fold =
        Objects.requireNonNull(changes.selectFirst("details.fold:contains(Points (a) and (b))"));
    var occurrences =
        Jsoup.parse(
            Objects.requireNonNull(
                http.get().uri(URI.create(fold.attr("hx-get"))).retrieve().body(String.class)));
    assertThat(occurrences.select("div.occurrence").eachAttr("id"))
        .containsExactly("anx/pnt_8.1.2.3/pnt_a", "anx/pnt_8.1.2.3/pnt_b");
  }

  @Test
  void servesOneDifferenceFromItsOwnAddress() {
    prepared();
    var difference = get(DIFFERENCE + "&eId=anx/pnt_8.1.2.3/pnt_c");
    var c = Objects.requireNonNull(difference.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(c.select("ins.diff").text())
        .isEqualTo(
            "applied in accordance with Attachment 6-J to the Annex to Implementing Decision"
                + " C(2015) 8005");
    // An occurrence with no difference, and rows of a table it does not have, are not found.
    assertThat(status(DIFFERENCE + "&eId=art_1")).isEqualTo(404);
    assertThat(status(DIFFERENCE + "&eId=anx/pnt_8.1.2.3/pnt_c&table=0&rows=0-3")).isEqualTo(404);
  }

  @Test
  void servesTheOutlineOfAnOccurrenceWithLinksToItsPlacesInRead() {
    prepared();
    var outline = get(DIFFERENCE + "&eId=anx&outline=true");
    var links = outline.select("ul.annex-outline a").eachAttr("href");
    assertThat(links).isNotEmpty().allMatch(href -> href.startsWith(ACT + "2026-10-01/eng#anx"));
    assertThat(status(DIFFERENCE + "&eId=no_such_occurrence&outline=true")).isEqualTo(404);
  }

  private int status(String uri) {
    return http.get().uri(uri).exchange((request, response) -> response.getStatusCode().value());
  }

  private Document get(String uri) {
    return Jsoup.parse(Objects.requireNonNull(http.get().uri(uri).retrieve().body(String.class)));
  }

  /**
   * The changes, once the act is prepared. Opening the compare page prepares the act, once the
   * catalogue has it: until then, the page waits for the lookup of the act, and asks again when it
   * ends, as this does. Until both texts are built, the changes are not found.
   */
  private Document prepared() {
    await()
        .atMost(Duration.ofMinutes(1))
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(250))
        .until(() -> get(PAGE).getElementById("act-lookup") == null);
    var body =
        await()
            .atMost(Duration.ofMinutes(1))
            // Poll at once. Otherwise Awaitility first waits an interval, even when the act is
            // already prepared.
            .pollDelay(Duration.ZERO)
            .pollInterval(Duration.ofMillis(250))
            .until(
                () ->
                    http.get()
                        .uri(CHANGES)
                        .exchange(
                            (request, response) ->
                                response.getStatusCode().is2xxSuccessful()
                                    ? new String(
                                        response.getBody().readAllBytes(), StandardCharsets.UTF_8)
                                    : ""),
                changes -> !changes.isEmpty());
    return Jsoup.parse(body);
  }
}
