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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Year;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's 23 versions on its timeline, as Cellar has them (recorded), read on the version of 31
 * December 2021: one of four within 102 days, which merge.
 */
class TimelineViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final String PAGES = "/eli/reg_impl/2015/1998/";

  final TimelineView timeline =
      TimelineView.of(AVSEC, version("02015R1998-20211231"), Language.ENG);

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
    var reading = timeline.marks().stream().filter(TimelineView.Mark::reading).toList();
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
        .filteredOn(Tick::reading)
        .extracting(Tick::href)
        .containsExactly(PAGES + "2021-12-31/eng");
    assertThat(timeline.reading()).isEqualTo(merged.at());
    assertThat(timeline.before()).isEqualTo(PAGES + "2021-07-01/eng");
    assertThat(timeline.after()).isEqualTo(PAGES + "2022-03-01/eng");
  }

  @Test
  void saysWhatEachVersionIs() {
    var ticks =
        timeline.marks().stream().filter(Tick.class::isInstance).map(Tick.class::cast).toList();
    assertThat(ticks.getFirst().label())
        .isEqualTo("32015R1998 · from 15 November 2015 · the act as adopted");
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
    var first = TimelineView.of(AVSEC, AVSEC.timeline().first(), Language.ENG);
    var latest = TimelineView.of(AVSEC, AVSEC.timeline().latest(), Language.ENG);

    assertThat(first.before()).isNull();
    assertThat(first.after()).isEqualTo(PAGES + "2016-02-29/eng");
    assertThat(latest.after()).isNull();
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
