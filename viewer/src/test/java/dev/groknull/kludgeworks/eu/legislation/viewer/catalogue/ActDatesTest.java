// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests what the dates of an act tell where no real act has the case: from when an act applies, and
 * the state of a change on a day. The act is made up. Its amending acts have the kinds of dates
 * that real acts have.
 */
class ActDatesTest {
  private static final ActIdentifier SECURITY = new ActIdentifier("32008R0300");
  private static final ActIdentifier SUPPLEMENT = new ActIdentifier("32009R0272");
  private static final ActIdentifier DIRECTIVE = new ActIdentifier("32010L0018");
  private static final ActIdentifier ONE_DAY = new ActIdentifier("32022L2556");
  private static final ActIdentifier PAYMENTS = new ActIdentifier("32015L2366");
  private static final ActIdentifier EURATOM = new ActIdentifier("31993R0001");
  private static final ActIdentifier UNTITLED = new ActIdentifier("32010R0185");
  private static final ActIdentifier RECENT = new ActIdentifier("32015R1998");

  /** Enters into force and applies in part on one day. Applies in full from a later day. */
  private static final ActIdentifier PARTS = new ActIdentifier("32022R1190");

  private static final LocalDate IN_PART = LocalDate.of(2022, 8, 1);
  private static final LocalDate IN_FULL = LocalDate.of(2026, 10, 15);

  private static final Act ACT =
      new Act(
          SECURITY,
          null,
          new ResourceType("REG"),
          Map.of(),
          null,
          true,
          null,
          List.of(),
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2008, 4, 29),
                      SECURITY,
                      EnumSet.allOf(Language.class),
                      Map.of()))),
          new ActDates(
              SECURITY,
              List.of(),
              Map.of(
                  PAYMENTS,
                  List.of(date(LocalDate.of(2016, 1, 12), ActDate.Kind.ENTRY_INTO_FORCE, false)),
                  ONE_DAY,
                  List.of(
                      date(LocalDate.of(2023, 1, 16), ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(LocalDate.of(2025, 1, 17), ActDate.Kind.TRANSPOSITION, false)),
                  DIRECTIVE,
                  List.of(
                      date(LocalDate.of(2010, 4, 7), ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(LocalDate.of(2012, 3, 8), ActDate.Kind.TRANSPOSITION, false),
                      date(LocalDate.of(2013, 3, 8), ActDate.Kind.TRANSPOSITION, false)),
                  SUPPLEMENT,
                  List.of(
                      date(LocalDate.of(2009, 4, 22), ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(LocalDate.of(2009, 6, 1), ActDate.Kind.APPLICATION, true)),
                  RECENT,
                  List.of(
                      date(LocalDate.of(2015, 11, 15), ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(LocalDate.of(2016, 2, 1), ActDate.Kind.APPLICATION, false)),
                  EURATOM,
                  List.of(
                      date(LocalDate.of(1993, 1, 7), ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(null, ActDate.Kind.APPLICATION, false)),
                  PARTS,
                  List.of(
                      date(IN_PART, ActDate.Kind.ENTRY_INTO_FORCE, false),
                      date(IN_PART, ActDate.Kind.APPLICATION, true),
                      date(IN_FULL, ActDate.Kind.APPLICATION, false)))),
          List.of(),
          Map.of());

  /**
   * 2024/1689 as published. It enters into force on 1 August 2024, applies in part from 2 February
   * 2025 and in full from 2 August 2026. Article 113 sets one more day that Cellar does not know.
   */
  private static final Act PHASED =
      new Act(
          new ActIdentifier("32024R1689"),
          null,
          new ResourceType("REG"),
          Map.of(),
          null,
          true,
          null,
          List.of(),
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2024, 8, 1),
                      new ActIdentifier("32024R1689"),
                      EnumSet.allOf(Language.class),
                      Map.of()))),
          new ActDates(
              new ActIdentifier("32024R1689"),
              List.of(
                  date(LocalDate.of(2024, 8, 1), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(LocalDate.of(2025, 2, 2), ActDate.Kind.APPLICATION, true),
                  date(LocalDate.of(2026, 8, 2), ActDate.Kind.APPLICATION, false),
                  new ActDate(
                      null,
                      ActDate.Kind.APPLICATION,
                      true,
                      new ActDate.Clause(
                          null,
                          null,
                          List.of(new ActDate.Provision(ActDate.Provision.Type.ARTICLE, "113")),
                          List.of(),
                          List.of(),
                          ""))),
              Map.of()),
          List.of(),
          Map.of());

  private static ActDate date(@Nullable LocalDate day, ActDate.Kind kind, boolean forPart) {
    return new ActDate(
        day, kind, forPart, new ActDate.Clause(null, null, List.of(), List.of(), List.of(), ""));
  }

  @Test
  void anActAsPublishedThatAppliesFromMoreThanOneDaySaysWhenEachPartStarts() {
    var published = PHASED.timeline().first();
    var inPart = LocalDate.of(2025, 2, 2);
    assertThat(PHASED.dates().appliesLater(published)).hasValue(inPart);
    assertThat(PHASED.dates().inPartFrom(inPart)).isTrue();
    assertThat(PHASED.dates().inFullAfter(inPart)).hasValue(LocalDate.of(2026, 8, 2));
    assertThat(PHASED.dates().inPartFrom(LocalDate.of(2026, 8, 2))).isFalse();
    assertThat(PHASED.dates().unknownDays())
        .containsExactly(new ActDate.Provision(ActDate.Provision.Type.ARTICLE, "113"));
  }

  @Test
  void anActAppliesInPartFromItsFirstDateForAPart() {
    assertThat(ACT.dates().appliesInPartOn(SUPPLEMENT, LocalDate.of(2009, 5, 31))).isFalse();
    assertThat(ACT.dates().appliesInPartOn(SUPPLEMENT, LocalDate.of(2009, 6, 1))).isTrue();
  }

  @Test
  void anActWithOneDateForTheWholeActAppliesFromIt() {
    assertThat(DatedAct.of(ACT, RECENT).applying(Set.of()))
        .get()
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(LocalDate.of(2016, 2, 1));
              assertThat(from.entryIntoForce()).isFalse();
            });
  }

  @Test
  void anActWithADateForAPartAppliesFromMoreThanOneDayUnlessCellarGivesTheDayOfTheProvision() {
    assertThat(DatedAct.of(ACT, SUPPLEMENT).applying(Set.of()))
        .get()
        .isInstanceOfSatisfying(
            Applying.FromMoreThanOneDay.class,
            parts -> {
              assertThat(parts.first()).isEqualTo(LocalDate.of(2009, 4, 22));
              assertThat(parts.starts())
                  .containsExactly(
                      new Applying.Start(LocalDate.of(2009, 4, 22), true, false),
                      new Applying.Start(LocalDate.of(2009, 6, 1), false, true));
            });
    assertThat(DatedAct.of(ACT, SUPPLEMENT).applying(Set.of(LocalDate.of(2009, 6, 1))))
        .get()
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> assertThat(from.day()).isEqualTo(LocalDate.of(2009, 6, 1)));
  }

  @Test
  void aDirectiveWithOnlyAnEntryIntoForceIsInForceWithoutADayItApplies() {
    assertThat(DatedAct.of(ACT, PAYMENTS).applying(Set.of(LocalDate.of(2016, 1, 12))))
        .get()
        .isInstanceOfSatisfying(
            Applying.InForce.class,
            inForce -> assertThat(inForce.day()).isEqualTo(LocalDate.of(2016, 1, 12)));
  }

  @Test
  void anActWithoutADayForItsApplicationAppliesFromADaySetByItsClauses() {
    assertThat(DatedAct.of(ACT, EURATOM).applying(Set.of()))
        .get()
        .isInstanceOf(Applying.SetBy.class);
    assertThat(DatedAct.of(ACT, UNTITLED).applying(Set.of())).isEmpty();
  }

  @Test
  void anActWithoutDatesHasNoDateFromWhichItApplies() {
    assertThat(DatedAct.of(ACT, UNTITLED).appliesFrom().from()).isEmpty();
  }

  @Test
  void aChangeAppliesFromTheDayThatCellarGivesForItsPlace() {
    assertThat(ACT.dates().changeAppliesFrom(PARTS, Set.of(IN_FULL))).hasValue(IN_FULL);
  }

  @Test
  void aChangeWithoutADayOfAnActFromMoreThanOneDayHasNoKnownStateBetweenTheParts() {
    assertThat(ACT.dates().changeAppliesFrom(PARTS, Set.of())).isEmpty();
    assertThat(DatedAct.of(ACT, PARTS).state(Set.of()).on(IN_PART).state())
        .isEqualTo(State.NOT_KNOWN);
    assertThat(DatedAct.of(ACT, PARTS).state(Set.of()).on(IN_FULL).state())
        .isEqualTo(State.APPLIES);
  }

  @Test
  void anActWithoutDatesHasNoKnownState() {
    assertThat(DatedAct.of(ACT, UNTITLED).state(Set.of()).on(IN_PART).state())
        .isEqualTo(State.NOT_KNOWN);
  }

  @Test
  void sourceConstantsAreOrderedFromMostCertainToLeastCertain() {
    assertThat(ActDate.Source.values())
        .containsExactly(
            ActDate.Source.CELLAR,
            ActDate.Source.CLAUSE_AND_JOURNAL,
            ActDate.Source.TEXT,
            ActDate.Source.NOT_CONFIRMED);
    assertThat(ActDate.Source.lessCertain(ActDate.Source.CELLAR, ActDate.Source.CLAUSE_AND_JOURNAL))
        .isEqualTo(ActDate.Source.CLAUSE_AND_JOURNAL);
    assertThat(ActDate.Source.lessCertain(ActDate.Source.NOT_CONFIRMED, ActDate.Source.TEXT))
        .isEqualTo(ActDate.Source.NOT_CONFIRMED);

    var cellar = date(IN_PART, ActDate.Kind.ENTRY_INTO_FORCE, false);
    var text = cellar.resolved(IN_PART, ActDate.Source.TEXT);
    assertThat(ActDate.Source.on(List.of(cellar, text), IN_PART)).contains(ActDate.Source.TEXT);
    assertThat(ActDate.Source.on(List.of(cellar), IN_FULL)).isEmpty();
  }
}
