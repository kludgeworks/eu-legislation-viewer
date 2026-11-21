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

  private static ActDate date(@Nullable LocalDate day, ActDate.Kind kind, boolean partial) {
    return new ActDate(
        day, kind, partial, new ActDate.Clause(null, null, List.of(), List.of(), ""));
  }

  private static Amendment change(
      ActIdentifier act, String place, @Nullable LocalDate from, Set<Language> languages) {
    return new Amendment(act, new Location(place), Role.REPLACEMENT, from, null, languages);
  }

  @Test
  void theAmendingActsAreNewestFirstWithoutCorrigendaAndWithoutActsForOtherLanguages() {
    assertThat(ACT.amendingActs(Language.ENG)).containsExactly(PHASED, EVENT, UNDATED, SIMPLE);
    assertThat(ACT.amendingActs(Language.POL))
        .containsExactly(PHASED, POLISH, EVENT, UNDATED, SIMPLE);
  }

  @Test
  void theAmendingActsBetweenTwoDaysAreTheOnesThatOnlyTheTextOfTheLaterDayIncludes() {
    // No version includes EVENT. The text of 1 January 2020 includes SIMPLE.
    assertThat(
            ACT.amendingActsBetween(
                LocalDate.of(2019, 1, 1), LocalDate.of(2022, 8, 1), Language.ENG, Map.of()))
        .hasValue(List.of(PHASED, UNDATED, SIMPLE));
    assertThat(
            ACT.amendingActsBetween(
                LocalDate.of(2020, 1, 1), LocalDate.of(2022, 8, 1), Language.ENG, Map.of()))
        .hasValue(List.of(PHASED, UNDATED));
  }

  @Test
  void aDayBeforeTheFirstVersionHasNoAmendingActsBetweenItAndALaterDay() {
    assertThat(
            ACT.amendingActsBetween(
                LocalDate.of(2018, 1, 1), LocalDate.of(2022, 8, 1), Language.ENG, Map.of()))
        .isEmpty();
  }

  @Test
  void theDaysOfChangeAreTheDaysFromWhichAnAmendingActOrAPartOfItApplies() {
    // EVENT applies from a day that Cellar does not know, and UNDATED has no dates: no day.
    // POLISH does not change the English text.
    var days = ACT.changeDays(Language.ENG);
    assertThat(days.keySet()).containsExactly(LocalDate.of(2019, 6, 11), IN_PART, IN_FULL);
    assertThat(days.get(LocalDate.of(2019, 6, 11))).containsExactly(SIMPLE);
    assertThat(days.get(IN_PART)).containsExactly(PHASED);
    assertThat(days.get(IN_FULL)).containsExactly(PHASED);
  }

  @Test
  void theChangesAreInTheOrderOfTheTextAndADeletedPlaceIsWhereItsOccurrenceIs() {
    var occurrences = List.of("art_3", "art_3/pnt_8", "art_20", "art_20/par_1", "art_79");
    var changes = ACT.changesBy(PHASED, Language.ENG);
    assertThat(Act.inTextOrder(changes, occurrences))
        .extracting(change -> change.location().code())
        .containsExactly("AR 3 PO 8", "AR 20 PA 1", "AR 79 PA 7");
  }

  @Test
  void theFirstVersionThatIntegratesAnActIsTheFirstTextWithItsChanges() {
    assertThat(ACT.integrating(PHASED, Language.ENG))
        .map(Version::date)
        .hasValue(LocalDate.of(2022, 8, 1));
    assertThat(ACT.integrating(EVENT, Language.ENG)).isEmpty();
  }

  @Test
  void theOtherActsOfTheVersionOfAnActAreWithoutCorrigendaAndWithoutTheActItself() {
    var shared =
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
                    version("2021-03-15", SIMPLE, EVENT, CORRIGENDUM))),
            List.of(),
            Map.of(),
            List.of(),
            Map.of());
    assertThat(shared.integratedWith(SIMPLE, Language.ENG)).containsExactly(EVENT);
    assertThat(shared.integratedWith(EVENT, Language.ENG)).containsExactly(SIMPLE);
    // 2022/1190 is the only act of its version. No version integrates 2021/1133.
    assertThat(ACT.integratedWith(PHASED, Language.ENG)).isEmpty();
    assertThat(ACT.integratedWith(EVENT, Language.ENG)).isEmpty();
  }

  @Test
  void theOccurrencesOfAChangeAreInTheTextWithTheActOrInTheTextBeforeItForADeletedPlace() {
    var change = change(PHASED, "AR 20 PA 1", IN_FULL, Set.of());
    var with = List.of("art_20", "art_20/par_1");
    var without = List.of("art_20");
    assertThat(Act.occurrences(change, with, without)).containsExactly("art_20/par_1");
    assertThat(Act.occurrences(change, without, with)).containsExactly("art_20/par_1");
    // Neither text has the paragraph itself: the change is in the article.
    assertThat(Act.occurrences(change, without, without)).containsExactly("art_20");
    assertThat(Act.occurrences(change, List.of("art_3"), List.of("art_3"))).isEmpty();
  }

  @Test
  void theCorrigendaOfAnActAreTheOnesPublishedInTheLanguage() {
    assertThat(ACT.corrigendaOf(BASE, Language.ENG)).containsExactly(CORRIGENDUM);
    assertThat(ACT.corrigendaOf(BASE, Language.DEU)).containsExactly(GERMAN_CORRIGENDUM);
    assertThat(ACT.corrigendaOf(PHASED, Language.ENG)).containsExactly(CORRIGENDUM_OF_PHASED);
  }

  @Test
  void theChangesOfAnActAreItsChangesToTheTextInTheLanguage() {
    assertThat(ACT.changesBy(PHASED, Language.ENG)).hasSize(3);
    assertThat(ACT.changesBy(POLISH, Language.ENG)).isEmpty();
    assertThat(ACT.changesBy(POLISH, Language.POL)).hasSize(1);
  }

  @Test
  void anActIsNotInForceBeforeItsEntryIntoForce() {
    assertThat(ACT.stateOn(PHASED, Set.of(IN_FULL), IN_PART.minusDays(1)))
        .isEqualTo(State.NOT_IN_FORCE);
  }

  @Test
  void aChangeWithADayAppliesFromThatDay() {
    assertThat(ACT.stateOn(PHASED, Set.of(IN_PART), IN_PART)).isEqualTo(State.APPLIES);
    assertThat(ACT.stateOn(PHASED, Set.of(IN_FULL), IN_PART)).isEqualTo(State.NOT_APPLYING);
    assertThat(ACT.stateOn(PHASED, Set.of(IN_FULL), IN_FULL)).isEqualTo(State.APPLIES);
    assertThat(ACT.changeAppliesFrom(PHASED, Set.of(IN_FULL))).hasValue(IN_FULL);
  }

  @Test
  void aChangeWithoutADayOfAnActInPartsHasNoKnownStateBetweenTheParts() {
    assertThat(ACT.changeAppliesFrom(PHASED, Set.of())).isEmpty();
    assertThat(ACT.stateOn(PHASED, Set.of(), IN_PART)).isEqualTo(State.NOT_KNOWN);
    assertThat(ACT.stateOn(PHASED, Set.of(), IN_FULL)).isEqualTo(State.APPLIES);
  }

  @Test
  void anActWithOnlyAnEntryIntoForceAppliesFromIt() {
    assertThat(ACT.stateOn(SIMPLE, Set.of(), LocalDate.of(2019, 6, 11))).isEqualTo(State.APPLIES);
    assertThat(ACT.stateOn(SIMPLE, Set.of(), LocalDate.of(2019, 6, 10)))
        .isEqualTo(State.NOT_IN_FORCE);
  }

  @Test
  void anActWithADaySetByAnEventOrWithoutDatesHasNoKnownState() {
    assertThat(ACT.stateOn(EVENT, Set.of(), LocalDate.of(2022, 8, 1))).isEqualTo(State.NOT_KNOWN);
    assertThat(ACT.stateOn(UNDATED, Set.of(), LocalDate.of(2022, 8, 1))).isEqualTo(State.NOT_KNOWN);
  }

  @Test
  void anActStandsAsItsChangesStand() {
    assertThat(ACT.standingOn(PHASED, Language.ENG, IN_PART))
        .isEqualTo(new Standing(false, 1, 2, 0))
        .extracting(Standing::whole)
        .isEqualTo(Standing.Whole.IN_PART);
    assertThat(ACT.standingOn(PHASED, Language.ENG, IN_FULL).whole())
        .isEqualTo(Standing.Whole.APPLIES);
    assertThat(ACT.standingOn(PHASED, Language.ENG, IN_PART.minusDays(1)).whole())
        .isEqualTo(Standing.Whole.NOT_IN_FORCE);
    assertThat(ACT.standingOn(EVENT, Language.ENG, IN_PART).whole())
        .isEqualTo(Standing.Whole.NOT_KNOWN);
  }

  @Test
  void anActWithoutChangedPlacesStandsAsTheWholeAct() {
    assertThat(ACT.standingOn(SIMPLE, Language.ENG, IN_PART))
        .isEqualTo(new Standing(false, 1, 0, 0));
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

  @Test
  void theHistoryOfAProvisionHasItsChangesAndTheOtherActsThatTheTextTagsItWith() {
    var history = ACT.historyOf("art_20/par_1", TEXT, Language.ENG);

    assertThat(history.changes())
        .extracting(change -> change.location().code())
        .containsExactly("AR 20 PA 1");
    assertThat(history.tagging()).containsExactly(SIMPLE);
    assertThat(history.acts()).containsExactly(PHASED, SIMPLE);
  }

  @Test
  void aChangeOfTheOccurrenceThatHoldsAProvisionIsInItsHistoryAndAChangeInItIsNot() {
    assertThat(ACT.historyOf("art_20/par_1/pnt_a", TEXT, Language.ENG).changes())
        .extracting(change -> change.location().code())
        .containsExactly("AR 20 PA 1");
    assertThat(ACT.historyOf("art_20", TEXT, Language.ENG).acts()).isEmpty();
  }

  @Test
  void anOccurrenceThatTheActAsPublishedDoesNotHaveAndThatTheDataNamesNoActForIsNotAttributed() {
    var versions = ACT.timeline().versions();
    var texts =
        Map.of(
            versions.getFirst(),
            new TextTags(List.of("art_3", "art_20", "art_20/par_1"), Map.of()),
            versions.getLast(),
            TEXT);

    // Article 3, point 8 has a change. Point (a) is in a paragraph that has one. Article 22 has a
    // tag in it. Article 21(1) is in Article 21.
    assertThat(ACT.unattributed(versions.getLast(), Language.ENG, texts)).containsExactly("art_21");
    assertThat(ACT.firstWith("art_21", Language.ENG, texts)).hasValue(versions.getLast());
    assertThat(ACT.unattributed(versions.getFirst(), Language.ENG, texts)).isEmpty();
  }
}
