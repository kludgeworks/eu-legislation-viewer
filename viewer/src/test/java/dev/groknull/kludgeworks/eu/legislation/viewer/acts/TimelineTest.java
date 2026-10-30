// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineTest {
  static final ActIdentifier ACT = new ActIdentifier("32015R1998");
  static final Version ORIGINAL = Version.original(LocalDate.of(2015, 11, 15), ACT);
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
        new Version(Version.Kind.CONSOLIDATED, FIRST.date(), "02015R1998-X", false, List.of());

    assertThatIllegalArgumentException().isThrownBy(() -> new Timeline(List.of(FIRST, sameDay)));
    assertThatIllegalArgumentException().isThrownBy(() -> new Timeline(List.of()));
  }

  @Test
  void refusesTheNeighboursOfAnotherActsVersion() {
    var other = consolidated("2020-01-01", false);

    assertThatIllegalArgumentException().isThrownBy(() -> TIMELINE.successor(other));
  }

  private static Version consolidated(String date, boolean interim) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        LocalDate.parse(date),
        "02015R1998-" + date.replace("-", ""),
        interim,
        List.of());
  }
}
