// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests how a card states the period of an act as published that applies in part. */
class WordingTest {
  private static final ActIdentifier AI = new ActIdentifier("32024R1689");
  private static final LocalDate IN_FORCE = LocalDate.of(2024, 8, 1);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
  private static final ActDate.Clause CLAUSE =
      new ActDate.Clause(
          null,
          null,
          List.of(new ActDate.Provision(ActDate.Provision.Type.ARTICLE, "113")),
          List.of(),
          "");

  private static Act act(List<ActDate> dates) {
    var original = Version.original(IN_FORCE, AI, EnumSet.allOf(Language.class), Map.of());
    return new Act(
        AI,
        null,
        new ResourceType("REG"),
        Map.of(),
        null,
        true,
        null,
        new Timeline(List.of(original)),
        dates,
        Map.of(),
        List.of(),
        Map.of());
  }

  private static String period(Act act) {
    return Wording.period(act, act.timeline().versions().getFirst(), TODAY, Language.ENG);
  }

  @Test
  void spellsOutWhenAnActAppliesInPartAndWhenInFull() {
    var act =
        act(
            List.of(
                new ActDate(IN_FORCE, Kind.ENTRY_INTO_FORCE, false, CLAUSE),
                new ActDate(LocalDate.of(2025, 2, 2), Kind.APPLICATION, true, CLAUSE),
                new ActDate(LocalDate.of(2026, 8, 2), Kind.APPLICATION, false, CLAUSE)));

    assertThat(period(act))
        .startsWith(
            "In force from 1 August 2024 · applies in part from 2 February 2025, in full from 2"
                + " August 2026");
  }

  @Test
  void namesTheClauseThatSetsTheOtherDatesOfAnActWithNoDateForTheWholeAct() {
    var act =
        act(
            List.of(
                new ActDate(IN_FORCE, Kind.ENTRY_INTO_FORCE, false, CLAUSE),
                new ActDate(LocalDate.of(2025, 2, 2), Kind.APPLICATION, true, CLAUSE),
                new ActDate(null, Kind.APPLICATION, true, CLAUSE)));

    assertThat(period(act))
        .startsWith(
            "In force from 1 August 2024 · applies in part from 2 February 2025; other parts from"
                + " dates set in Article 113");
  }
}
