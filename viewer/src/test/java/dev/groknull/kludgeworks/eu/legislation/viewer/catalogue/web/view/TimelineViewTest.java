// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Ball;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Break;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Dot;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Step;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Year;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the 23 versions of 2015/1998 on the book ruler of {@link TimelineView}. The versions are
 * Cellar's, recorded. Today is 5 November 2026. The tests read the law on 14 February 2022, which
 * the version of 31 December 2021 gives. They also compare 6 February 2018 with 2 July 2020.
 */
class TimelineViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final String PAGES = "/eli/reg_impl/2015/1998/";
  static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  final TimelineView timeline =
      TimelineView.reading(
          AVSEC,
          version("02015R1998-20211231"),
          LocalDate.of(2022, 2, 14),
          Language.ENG,
          TODAY,
          Map.of());

  @Test
  void spansTheYearsFromAdoptionToToday() {
    assertThat(timeline.years()).extracting(Year::year).startsWith(2015).endsWith(2026);
    assertThat(timeline.years().getFirst().at()).isZero();
    assertThat(timeline.adopted())
        .extracting(TimelineView.Point::label)
        .isEqualTo("Adopted 5 November 2015 · in force 15 November 2015");
    assertThat(timeline.today().label()).isEqualTo("Today, 5 November 2026");
    assertThat(timeline.dots()).hasSize(23).noneMatch(Dot::future);
  }

  @Test
  void widensEachYearByItsMarksAndSpacesThemEvenly() {
    // A year is as wide as its versions, plus one: 2016 has 1 version, 2022 has 4.
    assertThat(versionsIn(2016)).isEqualTo(1);
    assertThat(versionsIn(2022)).isEqualTo(4);
    var years = timeline.years();
    assertThat(width(years, 2022) / width(years, 2016)).isCloseTo(5.0 / 2, offset(0.01));
    var in2022 = timeline.dots().stream().filter(dot -> dot.label().contains(" 2022 · ")).toList();
    assertThat(in2022).hasSize(4);
    assertThat(in2022.get(1).at() - in2022.get(0).at())
        .isCloseTo(in2022.get(3).at() - in2022.get(2).at(), offset(0.001));
  }

  @Test
  void foldsARunOfYearsWithNoVersionIntoOneBreak() {
    var act = act(LocalDate.of(2009, 12, 1), "2010-01-01", "2012-01-01", "2014-07-01");
    var ruler = TimelineView.before(act, Language.ENG, LocalDate.of(2019, 6, 1));

    assertThat(ruler.years()).extracting(Year::year).containsExactly(2009, 2010, 2012, 2014, 2019);
    assertThat(ruler.breaks())
        .extracting(Break::label)
        .containsExactly("2011: no versions", "2013: no versions", "2015–18: no versions");
  }

  @Test
  void putsTheDayReadBetweenTheDotsOfItsVersions() {
    var ball = timeline.balls().getFirst();
    assertThat(ball.kicker()).isEqualTo("THE LAW ON");
    assertThat(ball.day()).isEqualTo("14 Feb 2022");
    assertThat(ball.at())
        .isGreaterThan(dot(timeline, "02015R1998-20211231").at())
        .isLessThan(dot(timeline, "02015R1998-20220301").at());
    assertThat(timeline.band()).isNull();
  }

  @Test
  void marksTheVersionBeingReadAndLinksEachToItsFirstDay() {
    assertThat(timeline.dots())
        .filteredOn(Dot::selected)
        .extracting(Dot::href)
        .containsExactly(PAGES + "2021-12-31/eng");
    assertThat(dot(timeline, "02015R1998-20220301").href()).isEqualTo(PAGES + "2022-03-01/eng");
    assertThat(timeline.steps())
        .extracting(Step::href)
        .containsExactly(PAGES + "2021-07-01/eng", PAGES + "2022-03-01/eng");
  }

  @Test
  void saysWhatEachVersionIs() {
    assertThat(timeline.dots().getFirst().label())
        .isEqualTo("15 November 2015 · the act as published · version 1");
    assertThat(dot(timeline, "02015R1998-20180206").label())
        .isEqualTo("6 February 2018 · made by 2018/55 · version 4");
  }

  @Test
  void hasNoVersionBeforeTheFirstNorAfterTheLatest() {
    var first = reading(AVSEC.timeline().first());
    var latest = reading(AVSEC.timeline().latest());

    assertThat(first.steps())
        .extracting(Step::href)
        .containsExactly(null, PAGES + "2016-02-29/eng");
    assertThat(latest.steps().getLast().href()).isNull();
  }

  @Test
  void dashesAVersionAfterToday() {
    var before =
        TimelineView.reading(
            AVSEC,
            version("02015R1998-20260322"),
            LocalDate.of(2026, 5, 1),
            Language.ENG,
            LocalDate.of(2026, 5, 1),
            Map.of());

    assertThat(dot(before, "02015R1998-20261001").future()).isTrue();
    assertThat(dot(before, "02015R1998-20260322").future()).isFalse();
  }

  @Test
  void movesTheNearerEndOfAComparisonToTheVersionClicked() {
    var comparing = comparing("02015R1998-20180206", "02015R1998-20200702", null);

    assertThat(dot(comparing, "02015R1998-20170607").href())
        .isEqualTo(PAGES + "eng/compare?from=2017-06-07&to=2020-07-02");
    assertThat(dot(comparing, "02015R1998-20211231").href())
        .isEqualTo(PAGES + "eng/compare?from=2018-02-06&to=2021-12-31");
    // Between them, 1 February 2019 is nearer to From (360 days) than to To (517 days).
    assertThat(dot(comparing, "02015R1998-20190201").href())
        .isEqualTo(PAGES + "eng/compare?from=2019-02-01&to=2020-07-02");
    assertThat(dot(comparing, "02015R1998-20200401").href())
        .isEqualTo(PAGES + "eng/compare?from=2018-02-06&to=2020-04-01");
    assertThat(comparing.balls()).extracting(Ball::kicker).containsExactly("FROM", "TO");
    assertThat(comparing.band()).isNotNull();
    assertThat(comparing.dots()).filteredOn(Dot::selected).hasSize(2);
  }

  @Test
  void keepsTheViewOfTheComparisonOnEveryLink() {
    var comparing = comparing("02015R1998-20180206", "02015R1998-20200702", "whole");

    assertThat(comparing.dots())
        .extracting(Dot::href)
        .allMatch(href -> href.endsWith("&view=whole"));
    assertThat(comparing.steps())
        .extracting(Step::href)
        .filteredOn(Objects::nonNull)
        .allMatch(href -> href.endsWith("&view=whole"));
  }

  @Test
  void stacksTheLabelsOfTwoDaysCloseTogetherNearTheEnd() {
    var comparing = comparing("02015R1998-20260322", "02015R1998-20261001", null);

    assertThat(comparing.balls())
        .extracting(Ball::kicker, Ball::end, Ball::stacked)
        .containsExactly(tuple("FROM", true, false), tuple("TO", true, true));
    var apart = comparing("02015R1998-20180206", "02015R1998-20200702", null);
    assertThat(apart.balls())
        .extracting(Ball::end, Ball::stacked)
        .containsExactly(tuple(true, false), tuple(false, false));
  }

  @Test
  void stepsBothEndsOfAComparison() {
    var comparing = comparing("02015R1998-20180206", "02015R1998-20200702", null);

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
                    + AVSEC.versionOn(TODAY, Language.ENG).orElseThrow().date()));
  }

  @Test
  void dashesTheVersionsThatFailed() {
    var failed = version("02015R1998-20211231");

    var reading =
        TimelineView.reading(
            AVSEC, failed, failed.date(), Language.ENG, TODAY, Map.of(failed, "failed"));

    assertThat(reading.dots())
        .filteredOn(dot -> "failed".equals(dot.state()))
        .singleElement()
        .satisfies(dot -> assertThat(dot.label()).endsWith(" · couldn't be prepared"));
  }

  @Test
  void hasNoStepToTheComparisonItIs() {
    var comparing = comparing("02015R1998-20200401", "02015R1998-20200702", null);

    assertThat(comparing.steps().get(2).href()).isNull();
  }

  @Test
  void comparisonCurrentVersionDoesNotPickFutureVersionWhenTodayLacksLanguage() {
    var original =
        new Version(
            Version.Kind.ORIGINAL,
            LocalDate.of(2010, 1, 1),
            "32010R0185",
            false,
            List.of(),
            Set.of(Language.ENG, Language.HRV),
            Instant.EPOCH);
    var intermediate =
        new Version(
            Version.Kind.CONSOLIDATED,
            LocalDate.of(2012, 1, 1),
            "02010R0185-20120101",
            false,
            List.of(),
            Set.of(Language.ENG, Language.HRV),
            Instant.EPOCH);
    var missingHrv =
        new Version(
            Version.Kind.CONSOLIDATED,
            LocalDate.of(2014, 7, 1),
            "02010R0185-20140701",
            false,
            List.of(),
            Set.of(Language.ENG),
            Instant.EPOCH);
    var future =
        new Version(
            Version.Kind.CONSOLIDATED,
            LocalDate.of(2015, 3, 1),
            "02010R0185-20150301",
            false,
            List.of(),
            Set.of(Language.ENG, Language.HRV),
            Instant.EPOCH);
    var act =
        new Act(
            AVSEC.id(),
            AVSEC.eli(),
            AVSEC.type(),
            AVSEC.titles(),
            AVSEC.dateOfDocument(),
            AVSEC.inForce(),
            AVSEC.endOfValidity(),
            new Timeline(List.of(original, intermediate, missingHrv, future)),
            AVSEC.dates(),
            AVSEC.amendingDates(),
            AVSEC.amendments(),
            AVSEC.publications());
    // On 2014-08-01, the consolidation in force is 2014-07-01, which lacks Croatian.
    // The previous Croatian version is 2012-01-01, while 2015-03-01 is in the future.
    var today = LocalDate.of(2014, 8, 1);
    var comparing =
        TimelineView.comparing(
            act,
            intermediate,
            intermediate.date(),
            future,
            future.date(),
            Language.HRV,
            today,
            Map.of(),
            null);

    assertThat(comparing.steps().get(3).label())
        .isEqualTo("compare the original with the current version");
    assertThat(comparing.steps().get(3).href())
        .isEqualTo(PAGES + "hrv/compare?from=2010-01-01&to=2012-01-01");
  }

  private static TimelineView reading(Version version) {
    return TimelineView.reading(AVSEC, version, version.date(), Language.ENG, TODAY, Map.of());
  }

  private static TimelineView comparing(String from, String to, @Nullable String view) {
    var earlier = version(from);
    var later = version(to);
    return TimelineView.comparing(
        AVSEC, earlier, earlier.date(), later, later.date(), Language.ENG, TODAY, Map.of(), view);
  }

  /** The dot of the version {@code celex}. */
  private static Dot dot(TimelineView timeline, String celex) {
    var words = Dates.inWords(version(celex).date()) + " · ";
    return timeline.dots().stream()
        .filter(dot -> dot.label().startsWith(words))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no dot of " + celex));
  }

  private static long versionsIn(int year) {
    return AVSEC.timeline().versions().stream().filter(v -> v.date().getYear() == year).count();
  }

  /** The width of the segment of {@code year}: from its start to the start of the next. */
  private static double width(List<Year> years, int year) {
    for (var i = 0; i < years.size() - 1; i++) {
      if (years.get(i).year() == year) {
        return years.get(i + 1).at() - years.get(i).at();
      }
    }
    throw new AssertionError("no year " + year);
  }

  /** An act adopted on {@code adopted}, with versions on {@code dates}, in English. */
  private static Act act(LocalDate adopted, String... dates) {
    var versions =
        java.util.Arrays.stream(dates)
            .map(
                date ->
                    new Version(
                        Version.Kind.CONSOLIDATED,
                        LocalDate.parse(date),
                        "02009R0001-" + date.replace("-", ""),
                        false,
                        List.of(),
                        Set.of(Language.ENG),
                        Instant.EPOCH))
            .toList();
    return new Act(
        AVSEC.id(),
        AVSEC.eli(),
        AVSEC.type(),
        AVSEC.titles(),
        adopted,
        AVSEC.inForce(),
        AVSEC.endOfValidity(),
        new Timeline(versions),
        AVSEC.dates(),
        AVSEC.amendingDates(),
        AVSEC.amendments(),
        AVSEC.publications());
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
