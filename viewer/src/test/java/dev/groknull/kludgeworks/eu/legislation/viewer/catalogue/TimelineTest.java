// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimelineTest {
  static final ActIdentifier ACT = new ActIdentifier("32015R1998");
  static final Version ORIGINAL =
      Version.original(
          LocalDate.of(2015, 11, 15),
          ACT,
          EnumSet.allOf(Language.class),
          Instant.parse("2026-07-10T01:02:19.329Z"));
  static final Version FIRST = consolidated("2016-02-29", false);
  static final Version INTERIM = consolidated("2026-10-01", true);
  static final Timeline TIMELINE = new Timeline(List.of(ORIGINAL, FIRST, INTERIM));

  @Test
  void aVersionAppliesFromItsDateUntilTheNextOnes() {
    assertThat(TIMELINE.applicableOn(LocalDate.of(2015, 11, 15))).contains(ORIGINAL);
    assertThat(TIMELINE.applicableOn(LocalDate.of(2016, 2, 28))).contains(ORIGINAL);
    assertThat(TIMELINE.applicableOn(LocalDate.of(2016, 2, 29))).contains(FIRST);
    assertThat(TIMELINE.applicableOn(LocalDate.of(2026, 9, 30))).contains(FIRST);
  }

  @Test
  void anInterimVersionAppliesOnceItsDateHasPassed() {
    assertThat(TIMELINE.applicableOn(LocalDate.of(2026, 10, 1))).contains(INTERIM);
  }

  @Test
  void noVersionAppliesBeforeTheFirstAndTheLatestAppliesFromItsDateOn() {
    assertThat(TIMELINE.applicableOn(LocalDate.of(2015, 11, 14))).isEmpty();
    assertThat(TIMELINE.applicableOn(LocalDate.of(2099, 1, 1))).contains(INTERIM);
    assertThat(TIMELINE.first()).isEqualTo(ORIGINAL);
    assertThat(TIMELINE.latest()).isEqualTo(INTERIM);
    assertThat(INTERIM.isFuture(LocalDate.of(2026, 9, 30))).isTrue();
  }

  @Test
  void eachVersionKnowsItsNeighbours() {
    assertThat(TIMELINE.predecessor(ORIGINAL)).isEmpty();
    assertThat(TIMELINE.successor(ORIGINAL)).contains(FIRST);
    assertThat(TIMELINE.predecessor(INTERIM)).contains(FIRST);
    assertThat(TIMELINE.successor(INTERIM)).isEmpty();
  }

  @Test
  void ordersTheVersionsByDate() {
    var timeline = new Timeline(List.of(INTERIM, ORIGINAL, FIRST));

    assertThat(timeline.versions()).containsExactly(ORIGINAL, FIRST, INTERIM);
    assertThat(timeline).isEqualTo(TIMELINE).hasSameHashCodeAs(TIMELINE);
  }

  @Test
  void refusesTwoVersionsOnOneDayOrNone() {
    var sameDay =
        new Version(
            Version.Kind.CONSOLIDATED,
            FIRST.date(),
            "02015R1998-X",
            false,
            List.of(),
            EnumSet.allOf(Language.class),
            Instant.parse("2026-07-10T01:02:19.329Z"));

    assertThatIllegalArgumentException().isThrownBy(() -> new Timeline(List.of(FIRST, sameDay)));
    assertThatIllegalArgumentException().isThrownBy(() -> new Timeline(List.of()));
  }

  @Test
  void allowsSameDayOriginalAndConsolidationWithDisjointLanguages() {
    var sameDayOriginal =
        Version.original(
            FIRST.date(),
            ACT,
            EnumSet.of(Language.ENG, Language.FRA),
            Instant.parse("2026-07-10T01:02:19.329Z"));
    var sameDayConsolidated =
        new Version(
            Version.Kind.CONSOLIDATED,
            FIRST.date(),
            "02015R1998-X",
            false,
            List.of(),
            EnumSet.of(Language.DEU, Language.FIN),
            Instant.parse("2026-07-10T01:02:19.329Z"));

    var timeline = new Timeline(List.of(sameDayConsolidated, sameDayOriginal));

    assertThat(timeline.versions()).containsExactly(sameDayOriginal, sameDayConsolidated);

    var english = timeline.in(Language.ENG).orElseThrow();
    assertThat(english.versions()).containsExactly(sameDayOriginal);

    var german = timeline.in(Language.DEU).orElseThrow();
    assertThat(german.versions()).containsExactly(sameDayConsolidated);

    assertThat(timeline.in(Language.ITA)).isEmpty();
  }

  @Test
  void allowsSameDayVersionWhenLanguagesAreEmptyWhileFetching() {
    var sameDayOriginal =
        new Version(
            Version.Kind.ORIGINAL,
            FIRST.date(),
            "32015R1998",
            false,
            List.of(),
            EnumSet.of(Language.ENG),
            Instant.parse("2026-07-10T01:02:19.329Z"));
    var sameDayUnfetched =
        new Version(
            Version.Kind.CONSOLIDATED,
            FIRST.date(),
            "02015R1998-UNFETCHED",
            false,
            List.of(),
            Set.of(),
            null);

    var timeline = new Timeline(List.of(sameDayUnfetched, sameDayOriginal));

    assertThat(timeline.versions()).containsExactly(sameDayOriginal, sameDayUnfetched);
    assertThat(timeline.in(Language.ENG).orElseThrow().versions()).containsExactly(sameDayOriginal);
  }

  @Test
  void refusesLanguageAgnosticLookupsOnTimelineWithSameDayVersions() {
    var sameDayOriginal =
        Version.original(
            FIRST.date(), ACT, EnumSet.of(Language.ENG), Instant.parse("2026-07-10T01:02:19.329Z"));
    var sameDayConsolidated =
        new Version(
            Version.Kind.CONSOLIDATED,
            FIRST.date(),
            "02015R1998-X",
            false,
            List.of(),
            EnumSet.of(Language.DEU),
            Instant.parse("2026-07-10T01:02:19.329Z"));

    var timeline = new Timeline(List.of(sameDayConsolidated, sameDayOriginal));

    assertThatIllegalStateException().isThrownBy(() -> timeline.applicableOn(FIRST.date()));
    assertThatIllegalStateException().isThrownBy(() -> timeline.startingOn(FIRST.date()));
    assertThatIllegalStateException().isThrownBy(() -> timeline.predecessor(sameDayConsolidated));
    assertThatIllegalStateException().isThrownBy(() -> timeline.successor(sameDayOriginal));

    assertThat(timeline.applicableOn(FIRST.date(), Language.ENG)).contains(sameDayOriginal);
    assertThat(timeline.startingOn(FIRST.date(), Language.DEU)).contains(sameDayConsolidated);
    assertThat(timeline.in(Language.ENG).orElseThrow().applicableOn(FIRST.date()))
        .contains(sameDayOriginal);
  }

  @Test
  void refusesTheNeighboursOfAnotherActsVersion() {
    var other = consolidated("2020-01-01", false);

    assertThatIllegalArgumentException().isThrownBy(() -> TIMELINE.successor(other));
  }

  @Test
  void aVersionIsFoundByTheDayItStartsOnOnly() {
    assertThat(TIMELINE.startingOn(LocalDate.of(2016, 2, 29))).contains(FIRST);
    assertThat(TIMELINE.startingOn(LocalDate.of(2016, 3, 1))).isEmpty();
  }

  @Test
  void theVersionsInALanguagePassOverThoseWithoutIt() {
    var withoutIrish =
        new Version(
            Version.Kind.CONSOLIDATED,
            LocalDate.of(2020, 1, 1),
            "02015R1998-20200101",
            false,
            List.of(),
            EnumSet.of(Language.ENG),
            Instant.parse("2026-07-10T01:02:19.329Z"));
    var timeline = new Timeline(List.of(ORIGINAL, withoutIrish, INTERIM));

    var irish = timeline.in(Language.GLE).orElseThrow();

    assertThat(irish.versions()).containsExactly(ORIGINAL, INTERIM);
    assertThat(irish.successor(ORIGINAL)).contains(INTERIM);
    assertThat(timeline.in(Language.ENG)).hasValue(timeline);
    assertThat(new Timeline(List.of(withoutIrish)).in(Language.GLE)).isEmpty();
  }

  private static Version consolidated(String date, boolean interim) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        LocalDate.parse(date),
        "02015R1998-" + date.replace("-", ""),
        interim,
        List.of(),
        EnumSet.allOf(Language.class),
        Instant.parse("2026-07-10T01:02:19.329Z"));
  }
}
