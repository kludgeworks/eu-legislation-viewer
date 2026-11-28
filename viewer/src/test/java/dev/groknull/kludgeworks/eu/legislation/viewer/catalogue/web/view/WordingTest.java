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
 * which applies from a day that a clause sets by an event, and 2022/1190, which applies from more
 * than one day.
 */
class WordingTest {
  private static final ActIdentifier EVENT = new ActIdentifier("32019R0413");
  private static final ActIdentifier PHASED = new ActIdentifier("32022R1190");
  private static final ActDate UNDATED =
      new ActDate(
          null,
          ActDate.Kind.APPLICATION,
          false,
          new ActDate.Clause(null, null, List.of(), List.of(), List.of(), ""));

  @Test
  void saysThatTheDataHasNoDatesForAnActWithout() {
    assertThat(Wording.unknown(EVENT, null)).isEqualTo("The data has no dates for this act.");
  }

  @Test
  void saysThatTheDataDoesNotGiveTheDayWhereNoClauseIsNamed() {
    assertThat(Wording.dates(EVENT, new Applying.SetBy(List.of(UNDATED))))
        .isEqualTo("The data does not give the day from which it applies.");
    assertThat(Wording.unknown(EVENT, new Applying.InForce(LocalDate.of(2019, 3, 15), List.of())))
        .isEqualTo("The data does not give the day from which this applies.");
  }

  @Test
  void saysNothingForAChangeOfAnActWhoseDayASetByClauseGives() {
    assertThat(Wording.unknown(EVENT, new Applying.SetBy(List.of(UNDATED)))).isNull();
  }

  @Test
  void saysThatTheDataDoesNotGiveThePartOfAnActFromMoreThanOneDay() {
    var parts =
        new Applying.FromMoreThanOneDay(
            List.of(
                new Applying.Start(LocalDate.of(2022, 8, 1), true, true),
                new Applying.Start(LocalDate.of(2026, 10, 15), false, false)),
            LocalDate.of(2022, 8, 1),
            List.of());
    assertThat(Wording.unknown(PHASED, parts))
        .isEqualTo(
            "The data does not say from which of these days this change applies: 1 August 2022 or"
                + " 15 October 2026");
  }

  @Test
  void writesDateSourceNotesNextToEachDay() {
    var day1 = LocalDate.of(2022, 8, 1);
    var day2 = LocalDate.of(2026, 10, 15);
    var clause =
        new ActDate.Clause(
            ActDate.Anchor.PUBLICATION,
            1,
            List.of(new ActDate.Provision(ActDate.Provision.Type.ARTICLE, "2")),
            List.of(),
            List.of(),
            "DATPUB +1 V ART 2");
    var fromJournal =
        new ActDate(
            day1, ActDate.Kind.ENTRY_INTO_FORCE, false, clause, ActDate.Source.CLAUSE_AND_JOURNAL);
    var notConfirmed =
        new ActDate(day2, ActDate.Kind.APPLICATION, true, clause, ActDate.Source.NOT_CONFIRMED);
    var fromText = new ActDate(day1, ActDate.Kind.APPLICATION, false, clause, ActDate.Source.TEXT);

    assertThat(Wording.dates(PHASED, new Applying.From(day1, false, List.of(fromJournal))))
        .isEqualTo("From 1 August 2022 (from the Official Journal) · Article 2 of 2022/1190");
    assertThat(Wording.dates(PHASED, new Applying.InForce(day1, List.of(fromText))))
        .isEqualTo(
            "In force from 1 August 2022 (from the enacted text) · Article 2 of 2022/1190."
                + " The data does not give the day from which it applies.");

    var parts =
        new Applying.FromMoreThanOneDay(
            List.of(new Applying.Start(day1, true, true), new Applying.Start(day2, false, true)),
            day1,
            List.of(fromJournal, notConfirmed));
    assertThat(Wording.dates(PHASED, parts))
        .isEqualTo(
            "Applies from more than one day: 1 August 2022 (from the Official Journal) and 15"
                + " October 2026 (not confirmed) · Article 2 of 2022/1190");
    assertThat(Wording.unknown(PHASED, parts))
        .isEqualTo(
            "The data does not say from which of these days this change applies: 1 August 2022"
                + " (from the Official Journal) or 15 October 2026 (not confirmed) · Article 2 of"
                + " 2022/1190");
  }
}
