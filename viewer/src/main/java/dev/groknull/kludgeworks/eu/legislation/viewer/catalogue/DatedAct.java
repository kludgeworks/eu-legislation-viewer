// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.WholeAct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
   */
  public Optional<Applying> applying(Set<LocalDate> placeDays) {
    return WhenAChangeApplies.dateFromTheAct(dates, appliesFrom, canApplyFrom, placeDays);
  }

  /**
   * Returns the states of the act as its dates tell them, or of one change that it makes at a place
   * for which Cellar gives {@code placeDays}.
   */
  public Periods<LegalState> state(Set<LocalDate> placeDays) {
    return states(applying(placeDays).orElse(null), null, null, List.of());
  }

  /**
   * Returns the states of the act itself. It can apply in part, and after {@code endOfValidity} it
   * is no longer in force.
   */
  public Periods<LegalState> state(Act itself) {
    return states(
        applying(Set.of()).orElse(null),
        appliesFrom.from().orElse(null),
        itself.endOfValidity(),
        itself.repealedBy());
  }

  /**
   * Applies the rules of the state on a day: one rule for each state. It finds the days that the
   * rules compare a day with, and puts the days of each state in order.
   *
   * @param applying when the act, or the change, applies. Null if Cellar has no dates of the act.
   * @param inFull the date from which the whole act applies, for the act on its own. Null for one
   *     change, or if the data does not tell it.
   * @throws IllegalStateException if the rules do not give one state for each day
   */
  private Periods<LegalState> states(
      @Nullable Applying applying,
      @Nullable ActDate inFull,
      @Nullable LocalDate endOfValidity,
      List<ActIdentifier> repealedBy) {
    var oneMoreDay =
        dates.stream()
            .anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null);
    var entryIntoForce =
        dates.stream()
            .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
            .map(ActDate::date)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
    var whole = applying == null || inFull == null ? null : inFull.date();
    LocalDate first = null;
    LocalDate last = null;
    LocalDate part = null;
    if (whole != null) {
      // The act on its own: a date for a part is an exception for that part only.
      first = whole;
      last = whole;
      part =
          dates.stream()
              .filter(ActDate::forPart)
              .map(ActDate::date)
              .filter(Objects::nonNull)
              .filter(day -> day.isBefore(whole))
              .min(Comparator.naturalOrder())
              .orElse(null);
    } else if (applying instanceof Applying.From one) {
      first = one.day();
      last = one.day();
    } else if (applying instanceof Applying.FromMoreThanOneDay more) {
      first = more.first();
      last = oneMoreDay ? null : more.starts().getLast().day();
    }
    var days = new TheStateOnADay.Days(entryIntoForce, endOfValidity, first, last, part);
    var periods = new ArrayList<Periods.Period<LegalState>>();
    TheStateOnADay.notInForce(days)
        .ifPresent(span ->
            periods.add(
                period(span, new LegalState.NotInForce(Objects.requireNonNull(entryIntoForce)))));
    TheStateOnADay.noLongerInForce(days)
        .ifPresent(span ->
            periods.add(
                period(
                    span,
                    new LegalState.NoLongerInForce(
                        Objects.requireNonNull(endOfValidity), repealedBy))));
    TheStateOnADay.doesNotApplyYet(days)
        .ifPresent(span ->
            periods.add(
                period(span, new LegalState.NotApplying(Objects.requireNonNull(applying)))));
    TheStateOnADay.appliesInPart(days)
        .ifPresent(span ->
            periods.add(
                period(
                    span,
                    new LegalState.InPart(
                        Objects.requireNonNull(applying), Objects.requireNonNull(inFull)))));
    TheStateOnADay.applies(days)
        .ifPresent(span ->
            periods.add(period(span, new LegalState.Applies(Objects.requireNonNull(applying)))));
    TheStateOnADay.notKnown(days)
        .ifPresent(span ->
            periods.add(
                period(span, new LegalState.NotKnown(why(applying, oneMoreDay), applying))));
    periods.sort(
        Comparator.comparing(
            Periods.Period::from, Comparator.nullsFirst(Comparator.naturalOrder())));
    for (int i = 0; i < periods.size(); i++) {
      var from = i == 0 ? null : periods.get(i - 1).until();
      var end = i == periods.size() - 1 && periods.get(i).until() != null;
      if (!Objects.equals(periods.get(i).from(), from) || (i > 0 && from == null) || end) {
        throw new IllegalStateException(
            "The rules do not give one state for each day of " + id + ": " + periods);
      }
    }
    return new Periods<>(periods);
  }

  private static Periods.Period<LegalState> period(TheStateOnADay.Span span, LegalState state) {
    return new Periods.Period<>(span.from(), span.until(), state);
  }

  /** Returns why the data does not tell the state. */
  private static LegalState.Why why(@Nullable Applying applying, boolean oneMoreDay) {
    return switch (applying) {
      case null -> LegalState.Why.NO_DATES;
      case Applying.SetBy _ -> LegalState.Why.A_CLAUSE_SETS_THE_DAY;
      case Applying.InForce _ -> LegalState.Why.IN_FORCE_ONLY;
      case Applying.FromMoreThanOneDay _ ->
          oneMoreDay ? LegalState.Why.ONE_MORE_DAY : LegalState.Why.BETWEEN_THE_PARTS;
      case Applying.From _ ->
          throw new IllegalStateException("The data tells the state of " + applying);
    };
  }
}
