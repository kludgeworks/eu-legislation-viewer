// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.WholeAct;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the dates of one act tell: an act itself, or an act that amends it. {@link #of} applies the
 * rules of the feature "When an act applies" in order, one time. Each rule gives one of the facts
 * here.
 *
 * @param id the act
 * @param dates the dates of the act, as Cellar has them
 * @param forTheWholeAct the dates that are for the whole act
 * @param appliesFrom from which day the whole act applies
 * @param canApplyFrom the days that a provision of the act can apply from
 */
public record DatedAct(
    ActIdentifier id,
    List<ActDate> dates,
    List<ActDate> forTheWholeAct,
    WholeAct appliesFrom,
    Set<LocalDate> canApplyFrom) {

  public DatedAct {
    dates = List.copyOf(dates);
    forTheWholeAct = List.copyOf(forTheWholeAct);
    canApplyFrom = Set.copyOf(canApplyFrom);
  }

  /** Returns what the dates of {@code id} tell. {@code of} has the dates: it is the act itself. */
  public static DatedAct of(Act of, ActIdentifier id) {
    return of(of.dates(), id);
  }

  /** Returns what the dates of {@code id} tell. {@code all} are the dates that an act has. */
  public static DatedAct of(ActDates all, ActIdentifier id) {
    var dates = all.datesOf(id);
    var forTheWholeAct = WhenAnActApplies.dateForAPart(dates);
    var appliesFrom = WhenAnActApplies.wholeActAppliesFrom(forTheWholeAct, id.directive());
    var canApplyFrom = WhenAnActApplies.aDayTheActCanApplyFrom(dates, appliesFrom);
    return new DatedAct(id, dates, forTheWholeAct, appliesFrom, canApplyFrom);
  }

  /**
   * Returns when the act applies at a place for which Cellar gives {@code placeDays}. They are
   * empty for the act as a whole. The answer is empty if Cellar has no dates of the act.
   *
   * <p>The rule entry-into-force-only is an exception to the rule cellar-day-else-the-parts: it is
   * applied first, and the other rule only if it is not for this act.
   */
  public Optional<Applying> applying(Set<LocalDate> placeDays) {
    return WhenAChangeApplies.entryIntoForceOnly(dates, appliesFrom)
        .or(() ->
            WhenAChangeApplies.cellarDayElseTheParts(dates, appliesFrom, canApplyFrom, placeDays));
  }

  /**
   * Returns the states of the act as its dates tell them, or of one change that it makes at a place
   * for which Cellar gives {@code placeDays}.
   */
  public Periods<LegalState> state(Set<LocalDate> placeDays) {
    return TheStateOnADay.stateOnADay(applying(placeDays).orElse(null), dates, null, List.of());
  }

  /**
   * Returns the states of the act itself: after {@code endOfValidity}, it is no longer in force.
   */
  public Periods<LegalState> state(Act itself) {
    return TheStateOnADay.stateOnADay(
        applying(Set.of()).orElse(null), dates, itself.endOfValidity(), itself.repealedBy());
  }
}
