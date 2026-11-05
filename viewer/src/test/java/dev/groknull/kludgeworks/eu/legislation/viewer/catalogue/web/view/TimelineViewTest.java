// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Merged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Step;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Year;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's 23 versions on its timeline, as Cellar has them (recorded), read on the version of 31
 * December 2021: one of four within 102 days, which merge; and compared, from 6 February 2018 to 2
 * July 2020.
 */
class TimelineViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final String PAGES = "/eli/reg_impl/2015/1998/";
  static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  final TimelineView timeline =
      TimelineView.reading(AVSEC, version("02015R1998-20211231"), Language.ENG);

  @Test
  void spansTheYearsOfItsVersions() {
    assertThat(timeline.years()).extracting(Year::year).startsWith(2015).endsWith(2027);
    assertThat(timeline.years().getFirst().at()).isZero();
    assertThat(timeline.years().getLast().at()).isEqualTo(100);
  }

  @Test
  void mergesVersionsTooCloseToClickApart() {
    assertThat(timeline.marks()).hasSize(19);
    assertThat(timeline.marks())
        .filteredOn(Merged.class::isInstance)
        .map(Merged.class::cast)
        .extracting(merged -> merged.versions().size(), Merged::label)
        .containsExactly(
            tuple(2, "2 versions within 45 days"), tuple(4, "4 versions within 102 days"));
  }

  @Test
  void marksTheVersionBeingReadAndLinksEachToItsPage() {
    var reading = timeline.marks().stream().filter(TimelineView.Mark::selected).toList();
    assertThat(reading).singleElement().isInstanceOf(Merged.class);
    var merged = (Merged) reading.getFirst();
    assertThat(merged.versions())
        .extracting(Tick::href)
        .containsExactly(
            PAGES + "2021-12-31/eng",
            PAGES + "2022-03-01/eng",
            PAGES + "2022-04-04/eng",
            PAGES + "2022-04-12/eng");
    assertThat(merged.versions())
        .filteredOn(Tick::selected)
        .extracting(Tick::href)
        .containsExactly(PAGES + "2021-12-31/eng");
    assertThat(timeline.tags()).containsExactly(new Tag("READING", merged.at()));
    assertThat(timeline.band()).isNull();
    assertThat(timeline.steps())
        .extracting(Step::href)
        .containsExactly(PAGES + "2021-07-01/eng", PAGES + "2022-03-01/eng");
  }

  @Test
  void saysWhatEachVersionIs() {
    var ticks =
        timeline.marks().stream().filter(Tick.class::isInstance).map(Tick.class::cast).toList();
    assertThat(ticks.getFirst().label())
        .isEqualTo("32015R1998 · from 15 November 2015 · the act as published");
    assertThat(ticks)
        .extracting(Tick::label)
        .contains(
            "02015R1998-20180206 · from 6 February 2018 · no amending act listed for this version");
    assertThat(ticks)
        .extracting(Tick::label)
        .anySatisfy(
            label ->
                assertThat(label)
                    .startsWith("02015R1998-20170607 · from 7 June 2017 · made by ")
                    .contains("2017/815"));
  }

  @Test
  void hatchesTheLagUntilAVersionIncludes2017815() {
    assertThat(timeline.lags())
        .singleElement()
        .satisfies(
            lag -> {
              assertThat(lag.text())
                  .startsWith("On this date, 2017/815 already applied (from 1 June 2017")
                  .endsWith("No consolidated version includes it until 7 June 2017.");
              assertThat(lag.width()).isCloseTo(0.5, within(0.01));
            });
  }

  @Test
  void hasNoVersionBeforeTheFirstNorAfterTheLatest() {
    var first = TimelineView.reading(AVSEC, AVSEC.timeline().first(), Language.ENG);
    var latest = TimelineView.reading(AVSEC, AVSEC.timeline().latest(), Language.ENG);

    assertThat(first.steps())
        .extracting(Step::href)
        .containsExactly(null, PAGES + "2016-02-29/eng");
    assertThat(latest.steps().getLast().href()).isNull();
  }

  @Test
  void movesTheNearerEndOfAComparisonToTheVersionClicked() {
    var comparing = comparing("02015R1998-20180206", "02015R1998-20200702");

    assertThat(href(comparing, "02015R1998-20170607"))
        .isEqualTo(PAGES + "eng/compare?from=2017-06-07&to=2020-07-02");
    assertThat(href(comparing, "02015R1998-20211231"))
        .isEqualTo(PAGES + "eng/compare?from=2018-02-06&to=2021-12-31");
    // between them: 1 February 2019 is nearer From (360 days) than To (517)
    assertThat(href(comparing, "02015R1998-20190201"))
        .isEqualTo(PAGES + "eng/compare?from=2019-02-01&to=2020-07-02");
    // and 1 April 2020 nearer To, merged with 16 February 2020 as it is
    assertThat(href(comparing, "02015R1998-20200401"))
        .isEqualTo(PAGES + "eng/compare?from=2018-02-06&to=2020-04-01");
    assertThat(comparing.tags()).extracting(Tag::label).containsExactly("FROM", "TO");
    assertThat(comparing.band()).isNotNull();
    assertThat(comparing.marks()).filteredOn(TimelineView.Mark::selected).hasSize(2);
  }

  @Test
  void stepsBothEndsOfAComparison() {
    var comparing = comparing("02015R1998-20180206", "02015R1998-20200702");

    assertThat(comparing.steps())
        .extracting(Step::label, Step::href)
        .containsExactly(
            tuple(
                "‹ both one version earlier", PAGES + "eng/compare?from=2017-06-07&to=2020-04-01"),
            tuple("both one version later ›", PAGES + "eng/compare?from=2019-02-01&to=2021-03-15"),
            tuple(
                "compare “to” with the version before",
                PAGES + "eng/compare?from=2020-04-01&to=2020-07-02"),
            tuple(
                "compare the original with the current version",
                PAGES
                    + "eng/compare?from=2015-11-15&to="
                    + AVSEC.versionOn(TODAY).orElseThrow().date()));
  }

  @Test
  void hasNoStepToTheComparisonItIs() {
    var comparing = comparing("02015R1998-20200401", "02015R1998-20200702");

    assertThat(comparing.steps().get(2).href()).isNull();
  }

  private static TimelineView comparing(String from, String to) {
    return TimelineView.comparing(AVSEC, version(from), version(to), Language.ENG, TODAY);
  }

  /** Where the mark of the version {@code celex} links, merged or not. */
  private static String href(TimelineView timeline, String celex) {
    return timeline.marks().stream()
        .flatMap(
            mark ->
                mark instanceof Merged merged ? merged.versions().stream() : Stream.of((Tick) mark))
        .filter(tick -> tick.label().startsWith(celex + " "))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no mark of " + celex))
        .href();
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
