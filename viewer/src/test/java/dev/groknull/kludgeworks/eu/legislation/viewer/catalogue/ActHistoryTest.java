// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment.Role;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the history of an act: the acts that amend it, and the state of each change on a day. The
 * act is made up. Its amending acts have the kinds of dates that real acts have.
 */
class ActHistoryTest {
  private static final ActIdentifier BASE = new ActIdentifier("32018R1862");

  /** Enters into force and applies in part on one day. Applies in full from a later day. */
  private static final ActIdentifier PHASED = new ActIdentifier("32022R1190");

  /** Applies from a day that a clause sets by an event. Cellar does not know the day. */
  private static final ActIdentifier EVENT = new ActIdentifier("32021R1133");

  /** Has only an entry into force. */
  private static final ActIdentifier SIMPLE = new ActIdentifier("32019R0818");

  /** Cellar has no dates of it. A version integrates it. */
  private static final ActIdentifier UNDATED = new ActIdentifier("32020R0493");

  /** Corrects only the Polish text. */
  private static final ActIdentifier POLISH = new ActIdentifier("32021R1150");

  private static final ActIdentifier CORRIGENDUM = new ActIdentifier("32018R1862R(01)");
  private static final ActIdentifier CORRIGENDUM_OF_PHASED = new ActIdentifier("32022R1190R(01)");
  private static final ActIdentifier GERMAN_CORRIGENDUM = new ActIdentifier("32018R1862R(02)");

  private static final LocalDate IN_PART = LocalDate.of(2022, 8, 1);
  private static final LocalDate IN_FULL = LocalDate.of(2026, 10, 15);

  private static final Act ACT =
      new Act(
          BASE,
          null,
          new ResourceType("REG"),
          Map.of(),
          null,
          true,
          null,
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2018, 12, 27), BASE, EnumSet.allOf(Language.class), Map.of()),
                  version("2019-06-11", SIMPLE, CORRIGENDUM, GERMAN_CORRIGENDUM),
                  version("2020-05-01", UNDATED),
                  version("2022-08-01", PHASED, CORRIGENDUM_OF_PHASED))),
          List.of(),
          Map.of(
              PHASED,
              List.of(
                  date(IN_PART, ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(IN_PART, ActDate.Kind.APPLICATION, true),
                  date(IN_FULL, ActDate.Kind.APPLICATION, false)),
              EVENT,
              List.of(
                  date(LocalDate.of(2021, 8, 2), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(null, ActDate.Kind.APPLICATION, false)),
              SIMPLE,
              List.of(date(LocalDate.of(2019, 6, 11), ActDate.Kind.ENTRY_INTO_FORCE, false)),
              POLISH,
              List.of(date(LocalDate.of(2021, 8, 3), ActDate.Kind.ENTRY_INTO_FORCE, false))),
          List.of(
              change(PHASED, "AR 79 PA 7", IN_PART, Set.of()),
              change(PHASED, "AR 3 PO 8", IN_FULL, Set.of()),
              change(PHASED, "AR 20 PA 1", IN_FULL, Set.of()),
              change(EVENT, "AR 18a", null, Set.of()),
              change(POLISH, "AR 4", LocalDate.of(2021, 8, 3), Set.of(Language.POL))),
          Map.of(
              CORRIGENDUM,
              new Publication(null, Map.of(Language.ENG, "Corrigendum"), null),
              GERMAN_CORRIGENDUM,
              new Publication(null, Map.of(Language.DEU, "Berichtigung"), null)));

  private static Version version(String day, ActIdentifier... integrates) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        LocalDate.parse(day),
        "02018R1862-" + day.replace("-", ""),
        false,
        List.of(integrates),
        EnumSet.allOf(Language.class),
        Map.of());
  }

  private static ActDate date(@Nullable LocalDate day, ActDate.Kind kind, boolean forPart) {
    return new ActDate(
        day, kind, forPart, new ActDate.Clause(null, null, List.of(), List.of(), List.of(), ""));
  }

  private static Amendment change(
      ActIdentifier act, String place, @Nullable LocalDate from, Set<Language> languages) {
    return new Amendment(act, new Location(place), Role.REPLACEMENT, from, null, languages);
  }

  @Test
  void anActWithoutADatedDateHasTheFirstDayOfTheFirstVersionThatIntegratesIt() {
    // No real act has no dated date: the edge of the-order-of-the-amending-acts.
    assertThat(ACT.firstDay(UNDATED, Language.ENG)).hasValue(LocalDate.of(2020, 5, 1));
    assertThat(ACT.amendingActs(Language.ENG)).containsExactly(PHASED, EVENT, UNDATED, SIMPLE);
  }

  @Test
  void anActWithoutDatesChangesTheActOnNoDay() {
    // No real act has no dates: the edge of the-days-of-change.
    assertThat(ACT.changeDays(Language.ENG).values()).noneMatch(acts -> acts.contains(UNDATED));
  }

  @Test
  void theOccurrencesOfAChangeThatTheLaterTextDoesNotHaveAreThoseOfTheTextBefore() {
    // No real text has these cases: the edges of the-occurrences-of-a-change.
    var change = change(PHASED, "AR 20 PA 1", IN_FULL, Set.of());
    var with = List.of("art_20", "art_20/par_1");
    assertThat(Act.occurrences(change, List.of("art_3"), with)).containsExactly("art_20/par_1");
    assertThat(Act.occurrences(change, List.of("art_3"), List.of("art_3"))).isEmpty();
  }

  @Test
  void aChangeAppliesFromTheDayThatCellarGivesForItsPlace() {
    assertThat(ACT.changeAppliesFrom(PHASED, Set.of(IN_FULL))).hasValue(IN_FULL);
  }

  @Test
  void aChangeWithoutADayOfAnActFromMoreThanOneDayHasNoKnownStateBetweenTheParts() {
    assertThat(ACT.changeAppliesFrom(PHASED, Set.of())).isEmpty();
    assertThat(ACT.stateOn(PHASED, Set.of(), IN_PART)).isEqualTo(State.NOT_KNOWN);
    assertThat(ACT.stateOn(PHASED, Set.of(), IN_FULL)).isEqualTo(State.APPLIES);
  }

  @Test
  void anActWithoutDatesHasNoKnownState() {
    assertThat(ACT.stateOn(UNDATED, Set.of(), LocalDate.of(2022, 8, 1))).isEqualTo(State.NOT_KNOWN);
  }

  /** The tags of a text of the act, with the occurrences that the changes of the act are at. */
  private static final TextTags TEXT =
      new TextTags(
          List.of(
              "art_3",
              "art_3/pnt_8",
              "art_20",
              "art_20/par_1",
              "art_20/par_1/pnt_a",
              "art_21",
              "art_21/par_1",
              "art_22",
              "art_22/par_1"),
          Map.of(
              PHASED,
              Set.of("art_20/par_1"),
              SIMPLE,
              Set.of("art_20/par_1", "art_22/par_1"),
              BASE,
              Set.of("art_20/par_1")));

  @Test
  void theOccurrencesWithAChangeOfTheirOwnAreThePlacesThatTheTextHas() {
    assertThat(ACT.changedOccurrences(TEXT.eIds(), Language.ENG).keySet())
        .containsExactly("art_3/pnt_8", "art_20/par_1");
  }
}
