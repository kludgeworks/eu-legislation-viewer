// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the words for a change whose state on a day the data does not give. The acts are 2019/413,
 * which applies from a day that a clause sets by an event, and 2022/1190, which applies in parts.
 */
class StatesTest {
  private static final ActIdentifier EVENT = new ActIdentifier("32019R0413");
  private static final ActIdentifier PHASED = new ActIdentifier("32022R1190");
  private static final ActDate UNDATED =
      new ActDate(
          null,
          ActDate.Kind.APPLICATION,
          false,
          new ActDate.Clause(null, null, List.of(), List.of(), ""));

  @Test
  void saysThatTheDataHasNoDatesForAnActWithout() {
    assertThat(States.unknown(EVENT, null)).isEqualTo("The viewer has no dates for this act.");
  }

  @Test
  void saysThatTheDataDoesNotGiveTheDayWhereNoClauseIsNamed() {
    assertThat(States.dates(EVENT, new Applying.SetBy(List.of(UNDATED))))
        .isEqualTo("The viewer has no date for the day from which it applies.");
    assertThat(States.unknown(EVENT, new Applying.InForce(LocalDate.of(2019, 3, 15), List.of())))
        .isEqualTo("The viewer has no date for the day from which this applies.");
  }

  @Test
  void saysNothingForAChangeOfAnActWhoseDayASetByClauseGives() {
    assertThat(States.unknown(EVENT, new Applying.SetBy(List.of(UNDATED)))).isNull();
  }

  @Test
  void saysThatTheDataDoesNotGiveThePartOfAnActInParts() {
    var parts =
        new Applying.InParts(
            List.of(
                new Applying.Start(LocalDate.of(2022, 8, 1), true, true),
                new Applying.Start(LocalDate.of(2026, 10, 15), false, false)),
            LocalDate.of(2022, 8, 1),
            List.of());
    assertThat(States.unknown(PHASED, parts))
        .isEqualTo(
            "The viewer cannot tell from which of these days this change applies: 1 August 2022 or"
                + " 15 October 2026");
  }
}
