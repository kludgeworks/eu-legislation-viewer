// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment.Role;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the rule that compares the date of each changed provision with the first version that marks
 * it. The act is made up: a base act with an annex, and one amending act that replaces point 1.1.
 */
class DivergencesTest {
  private static final ActIdentifier BASE = new ActIdentifier("32015R1998");
  private static final ActIdentifier AMENDING = new ActIdentifier("32017R0815");
  private static final String POINT = "anx/pnt_1.1";
  private static final List<String> UNITS = List.of("anx", "anx/pnt_1", POINT, "anx/pnt_1.2");

  private final List<Planned> planned = new ArrayList<>();
  private final Map<ActIdentifier, List<ActDate>> dates = new HashMap<>();
  private @Nullable LocalDate placeDate;
  private Role role = Role.REPLACEMENT;
  private Set<Language> changedLanguages = Set.of();

  @Test
  void aProvisionThatAppliesBeforeTheFirstVersionThatMarksItMakesTheTextLate() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    assertThat(divergences())
        .extracting(Divergence::kind, Divergence::appliesFrom, d -> included(d), d -> places(d))
        .containsExactly(
            tuple(
                Divergence.Kind.LATE,
                LocalDate.parse("2017-06-01"),
                LocalDate.parse("2017-06-07"),
                List.of("AN PO 1.1")));
  }

  @Test
  void aProvisionThatAppliesOnTheDayOfTheFirstVersionThatMarksItAgreesWithTheLaw() {
    application("2017-06-07");
    place("2017-06-07");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void aProvisionThatAVersionMarksBeforeItAppliesMakesTheTextEarly() {
    entryIntoForce("2017-12-29");
    partialApplication("2019-10-11");
    place("2019-10-11");
    version("2016-02-29", false);
    version("2018-01-12", true, POINT);
    version("2020-01-01", true, POINT);

    assertThat(divergences())
        .extracting(Divergence::kind, Divergence::appliesFrom, d -> included(d))
        .containsExactly(
            tuple(
                Divergence.Kind.EARLY,
                LocalDate.parse("2019-10-11"),
                LocalDate.parse("2018-01-12")));
  }

  @Test
  void aPlaceDateThatIsOnlyTheEntryIntoForceIsRejectedWhenTheWholeActAppliesLater() {
    entryIntoForce("2017-05-20");
    application("2017-06-07");
    place("2017-05-20");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void aPlaceDateThatIsNotADateOfTheActIsRejected() {
    application("2017-06-07");
    place("2017-05-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void aVersionOnThePlaceDateThatIncludesTheActButDoesNotMarkTheProvisionKeepsTheVersionDate() {
    application("2017-06-01");
    partialApplication("2017-08-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-01", true, "anx/pnt_1.2");
    version("2017-08-01", true, POINT, "anx/pnt_1.2");

    assertThat(divergences()).isEmpty();
  }

  @Test
  void theVersionOnThePlaceDateIsTheVersionDateIfItNewlyMarksTheProvision() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-05-01", true, POINT);
    version("2017-05-15", true);
    version("2017-06-01", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void aProvisionOfAnActThatNoVersionIncludesMakesTheTextLateFromItsDate() {
    application("2026-11-12");
    place("2026-11-12");
    version("2016-02-29", false);
    version("2017-06-07", false);

    assertThat(divergences())
        .extracting(Divergence::kind, Divergence::appliesFrom, Divergence::included)
        .containsExactly(tuple(Divergence.Kind.LATE, LocalDate.parse("2026-11-12"), null));
  }

  @Test
  void aReplacedProvisionThatTheVersionBeforeDoesNotHaveMakesNoTextLate() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    versionWithoutThePoint("2017-03-01");
    version("2017-06-07", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void anAddedProvisionThatTheVersionBeforeDoesNotHaveMakesTheTextLate() {
    role = Role.ADDITION;
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    versionWithoutThePoint("2017-03-01");
    version("2017-06-07", true, POINT);

    assertThat(divergences())
        .extracting(Divergence::kind, d -> included(d))
        .containsExactly(tuple(Divergence.Kind.LATE, LocalDate.parse("2017-06-07")));
  }

  @Test
  void anActThatNoVersionIncludesMakesNoTextLateUntilEveryTextIsBuilt() {
    application("2026-11-12");
    place("2026-11-12");
    version("2016-02-29", false);
    version("2017-06-07", false);

    var act = act();
    var texts = new LinkedHashMap<>(texts(act));
    texts.remove(act.timeline().versions().getLast());

    assertThat(act.divergences(Language.ENG, texts)).isEmpty();
  }

  @Test
  void anActWhoseProvisionsNoVersionMarksIsComparedAsAWhole() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, "anx/pnt_1.2");

    assertThat(divergences())
        .extracting(Divergence::kind, Divergence::appliesFrom, d -> included(d), d -> places(d))
        .containsExactly(
            tuple(
                Divergence.Kind.LATE,
                LocalDate.parse("2017-06-01"),
                LocalDate.parse("2017-06-07"),
                List.of()));
  }

  @Test
  void withoutTheTextsAnActIsComparedAsAWhole() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    assertThat(act().divergences(Language.ENG, Map.of()))
        .extracting(Divergence::kind, Divergence::appliesFrom, d -> places(d))
        .containsExactly(tuple(Divergence.Kind.LATE, LocalDate.parse("2017-06-01"), List.of()));
  }

  @Test
  void anActThatNoVersionIncludesAndWhoseDayOfApplicationIsNotKnownIsNotConfirmed() {
    entryIntoForce("2021-08-02");
    applicationOnADayNotKnown();
    version("2016-02-29", false);

    assertThat(divergences())
        .extracting(Divergence::kind, Divergence::appliesFrom, d -> included(d))
        .containsExactly(tuple(Divergence.Kind.UNCONFIRMED, LocalDate.parse("2021-08-02"), null));
    assertThat(divergences().getFirst().on(LocalDate.parse("2021-08-01"))).isFalse();
    assertThat(divergences().getFirst().on(LocalDate.parse("2026-11-16"))).isTrue();
  }

  @Test
  void anActThatAVersionIncludesAndWhoseDayOfApplicationIsNotKnownTakesTheVersionDate() {
    entryIntoForce("2021-08-02");
    applicationOnADayNotKnown();
    version("2016-02-29", false);
    version("2022-01-01", true, POINT);

    assertThat(divergences()).isEmpty();
  }

  @Test
  void anActThatChangesOnlyOtherLanguagesMakesNoTextLate() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);
    changedLanguages = Set.of(Language.SWE, Language.POL);

    assertThat(divergences()).isEmpty();

    changedLanguages = Set.of(Language.SWE, Language.ENG);

    assertThat(divergences()).extracting(Divergence::kind).containsExactly(Divergence.Kind.LATE);
  }

  @Test
  void aLateProvisionIsItsUnitInTheTextOrTheUnitThatHoldsIt() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);

    var late = divergences().getFirst();

    assertThat(late.in(UNITS)).containsExactly(new Divergence.Unit(POINT, false));
    assertThat(late.in(List.of("anx", "anx/pnt_1", "anx/pnt_1.2")))
        .containsExactly(new Divergence.Unit("anx/pnt_1", true));
    assertThat(late.whole(BASE)).isFalse();
  }

  @Test
  void theLawWhileTheTextIsLateIsTheVersionBeforeWithTheChangesOfTheAct() {
    application("2017-06-01");
    place("2017-06-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);
    var act = act();
    var versions = act.timeline().versions();

    assertThat(act.unshown(versions.get(1), versions.get(2), AMENDING, Language.ENG, texts(act)))
        .contains(
            new Unshown(AMENDING, LocalDate.parse("2017-06-01"), LocalDate.parse("2017-06-06")));
    var unshown =
        act.unshown(versions.get(1), versions.get(2), AMENDING, Language.ENG, texts(act))
            .orElseThrow();
    assertThat(unshown.on(LocalDate.parse("2017-06-03"))).isTrue();
    assertThat(unshown.on(LocalDate.parse("2017-06-07"))).isFalse();
    assertThat(unshown.on(LocalDate.parse("2017-05-31"))).isFalse();
    // The version before that lacks more than the act.
    assertThat(act.unshown(versions.get(0), versions.get(2), AMENDING, Language.ENG, texts(act)))
        .isEmpty();
    // An act that does not make the text late.
    assertThat(act.unshown(versions.get(1), versions.get(2), BASE, Language.ENG, texts(act)))
        .isEmpty();
  }

  @Test
  void theLawWhileTheTextIsLateStartsNoEarlierThanTheVersionBefore() {
    application("2016-01-01");
    place("2016-01-01");
    version("2016-02-29", false);
    version("2017-06-07", true, POINT);
    var act = act();
    var versions = act.timeline().versions();

    assertThat(act.unshown(versions.get(1), versions.get(2), AMENDING, Language.ENG, texts(act)))
        .contains(
            new Unshown(AMENDING, LocalDate.parse("2016-02-29"), LocalDate.parse("2017-06-06")));
  }

  @Test
  void theEarlyTextOfTheActItselfIsTheWholeText() {
    var early =
        new Divergence(
            Divergence.Kind.EARLY,
            BASE,
            new ActDate(
                LocalDate.parse("2016-02-01"),
                Kind.APPLICATION,
                false,
                new ActDate.Clause(null, null, List.of(), List.of(), "")),
            Version.original(
                LocalDate.parse("2015-11-15"), BASE, EnumSet.of(Language.ENG), Map.of()),
            List.of());

    assertThat(early.whole(BASE)).isTrue();
    assertThat(early.in(UNITS)).isEmpty();
  }

  // ------------------------------------------------------------------------------------ helpers

  private void application(String day) {
    date(day, Kind.APPLICATION, false);
  }

  private void partialApplication(String day) {
    date(day, Kind.APPLICATION, true);
  }

  /** Adds a date of application of the whole act whose day Cellar has as a placeholder. */
  private void applicationOnADayNotKnown() {
    dates
        .computeIfAbsent(AMENDING, act -> new ArrayList<>())
        .add(
            new ActDate(
                null,
                Kind.APPLICATION,
                false,
                new ActDate.Clause(null, null, List.of(), List.of(), "")));
  }

  private void entryIntoForce(String day) {
    date(day, Kind.ENTRY_INTO_FORCE, false);
  }

  private void date(String day, Kind kind, boolean partial) {
    dates
        .computeIfAbsent(AMENDING, act -> new ArrayList<>())
        .add(
            new ActDate(
                LocalDate.parse(day),
                kind,
                partial,
                new ActDate.Clause(null, null, List.of(), List.of(), "")));
  }

  private void place(String day) {
    placeDate = LocalDate.parse(day);
  }

  /**
   * Adds a consolidated version that starts on {@code day}, whose text marks {@code units} with the
   * amending act. {@code lists} tells whether the Publications Office lists the act for it.
   */
  private void version(String day, boolean lists, String... units) {
    planned.add(new Planned(LocalDate.parse(day), lists, Set.of(units), UNITS));
  }

  /** Adds a consolidated version that starts on {@code day}, whose text does not have the point. */
  private void versionWithoutThePoint(String day) {
    planned.add(
        new Planned(
            LocalDate.parse(day),
            false,
            Set.of(),
            UNITS.stream().filter(unit -> !unit.equals(POINT)).toList()));
  }

  private List<Divergence> divergences() {
    var act = act();
    return act.divergences(Language.ENG, texts(act));
  }

  private Map<Version, TextMarks> texts(Act act) {
    var texts = new LinkedHashMap<Version, TextMarks>();
    var versions = act.timeline().versions();
    for (var i = 1; i < versions.size(); i++) {
      var each = planned.get(i - 1);
      var marked = each.marks();
      texts.put(
          versions.get(i),
          new TextMarks(each.units(), marked.isEmpty() ? Map.of() : Map.of(AMENDING, marked)));
    }
    texts.put(versions.getFirst(), new TextMarks(UNITS, Map.of()));
    return texts;
  }

  private Act act() {
    var languages = EnumSet.of(Language.ENG);
    var versions = new ArrayList<Version>();
    versions.add(Version.original(LocalDate.parse("2015-11-15"), BASE, languages, Map.of()));
    for (var each : planned) {
      versions.add(
          new Version(
              Version.Kind.CONSOLIDATED,
              each.start(),
              "02015R1998-" + each.start().toString().replace("-", ""),
              false,
              each.lists() ? List.of(AMENDING) : List.of(),
              languages,
              Map.of()));
    }
    return new Act(
        BASE,
        null,
        new ResourceType("REG_IMPL"),
        Map.of(),
        null,
        true,
        null,
        new Timeline(versions),
        List.of(),
        dates,
        List.of(
            new Amendment(
                AMENDING, new Location("AN PO 1.1"), role, placeDate, null, changedLanguages)),
        Map.of());
  }

  private static @Nullable LocalDate included(Divergence divergence) {
    var included = divergence.included();
    return included == null ? null : included.date();
  }

  private static List<String> places(Divergence divergence) {
    return divergence.places().stream()
        .map(place -> Objects.requireNonNull(place.location()).code())
        .toList();
  }

  /**
   * A consolidated version of the made-up act.
   *
   * @param start the day it starts
   * @param lists whether the Publications Office lists the amending act for it
   * @param marks the units that its text marks with the amending act
   * @param units the eIds of the units of its text
   */
  private record Planned(LocalDate start, boolean lists, Set<String> marks, List<String> units) {}
}
