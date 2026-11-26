// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The state on a day": one function for each of its rules. A rule reads facts and
 * gives one new fact. Each state has one rule, which gives the days of that state. On a day, one
 * rule is true and the others are not: {@link DatedAct} applies the rules and checks it. A {@link
 * LegalState} has the state and the facts that give it: a page puts them into words, and does not
 * look them up again.
 */
public final class TheStateOnADay {

  private TheStateOnADay() {}

  /** The state of an act, or of one change, on a day, with the facts that give it. */
  public sealed interface LegalState {

    /** Returns the state. */
    State state();

    /** The day is after the end of validity of the act. */
    record NoLongerInForce(LocalDate endOfValidity, List<ActIdentifier> repealedBy)
        implements LegalState {
      public NoLongerInForce {
        repealedBy = List.copyOf(repealedBy);
      }

      @Override
      public State state() {
        return State.NO_LONGER_IN_FORCE;
      }
    }

    /** The day is before the entry into force of the act. */
    record NotInForce(LocalDate entryIntoForce) implements LegalState {
      @Override
      public State state() {
        return State.NOT_IN_FORCE;
      }
    }

    /** The act is in force, and does not apply yet. */
    record NotApplying(Applying applying) implements LegalState {
      @Override
      public State state() {
        return State.NOT_APPLYING;
      }
    }

    /**
     * The act applies in part: a part of it applies, and the whole act does not apply yet. Only an
     * act on its own has this state.
     *
     * @param applying when the act applies
     * @param inFull the date from which the whole act applies, with the clause that sets it. It has
     *     a day.
     */
    record InPart(Applying applying, ActDate inFull) implements LegalState {
      public InPart {
        Objects.requireNonNull(inFull.date(), "an act that applies in part has the day it ends");
      }

      @Override
      public State state() {
        return State.IN_PART;
      }

      /** Returns the day from which the whole act applies. */
      public LocalDate inFullFrom() {
        return Objects.requireNonNull(inFull.date());
      }
    }

    /** The act, or the change, applies. */
    record Applies(Applying applying) implements LegalState {
      @Override
      public State state() {
        return State.APPLIES;
      }
    }

    /**
     * The data does not tell the state.
     *
     * @param why the reason
     * @param applying when the act applies, as far as the data tells, or null if Cellar has no
     *     dates of the act
     */
    record NotKnown(Why why, @Nullable Applying applying) implements LegalState {
      @Override
      public State state() {
        return State.NOT_KNOWN;
      }
    }

    /** Why the data does not tell a state. */
    enum Why {
      /** Cellar has no dates of the act. */
      NO_DATES,
      /** A clause sets the day, for example by an event, and Cellar does not give it. */
      A_CLAUSE_SETS_THE_DAY,
      /** The act is in force, and the data does not say from when it applies. */
      IN_FORCE_ONLY,
      /** The act applies from more than one day, and Cellar does not tell the part. */
      BETWEEN_THE_PARTS,
      /** A clause sets one more day that Cellar does not give. */
      ONE_MORE_DAY
    }
  }

  /**
   * How an amending act stands on a day: its state as a whole, and how many of its changes are in
   * each state.
   */
  public record Stands(State state, int applies, int notApplying, int notKnown) {}

  /**
   * The days that the rules compare a day with, for an act on its own or for one change.
   *
   * @param entryIntoForce the entry into force of the act, or null if Cellar does not give it
   * @param endOfValidity the end of validity of the act. Null if it has none, or for a change.
   * @param first the first day from which it can apply, or null if the data gives no such day
   * @param last the last day from which it can apply. It is {@code first} if it can apply from one
   *     day. Null if the data gives no day, or if a clause sets one more day that Cellar does not
   *     give.
   * @param part for an act on its own whose whole-act day the data tells: its first date for a part
   *     before that day. Else null.
   */
  public record Days(
      @Nullable LocalDate entryIntoForce,
      @Nullable LocalDate endOfValidity,
      @Nullable LocalDate first,
      @Nullable LocalDate last,
      @Nullable LocalDate part) {

    /**
     * Returns the days on which the act is in force: not before its entry into force, and not after
     * its end of validity.
     */
    public Span inForce() {
      return new Span(entryIntoForce, endOfValidity == null ? null : endOfValidity.plusDays(1));
    }
  }

  /**
   * Days in sequence, on which a state is true.
   *
   * @param from the first day, or null if each earlier day is in it
   * @param until the first day after it, or null if each later day is in it
   */
  public record Span(@Nullable LocalDate from, @Nullable LocalDate until) {

    /** Returns the days from {@code from} to the day before {@code until}, or empty if none. */
    static Optional<Span> of(@Nullable LocalDate from, @Nullable LocalDate until) {
      return from != null && until != null && !from.isBefore(until)
          ? Optional.empty()
          : Optional.of(new Span(from, until));
    }

    /** Returns the later of two first days. Null is before each day. */
    static @Nullable LocalDate later(@Nullable LocalDate one, @Nullable LocalDate other) {
      if (one == null) {
        return other;
      }
      return other == null || one.isAfter(other) ? one : other;
    }

    /** Returns the earlier of two days. Null is after each day. */
    static @Nullable LocalDate earlier(@Nullable LocalDate one, @Nullable LocalDate other) {
      if (one == null) {
        return other;
      }
      return other == null || one.isBefore(other) ? one : other;
    }
  }

  /**
   * Before the entry into force of the act, the act is not in force. A change of the act is not in
   * force also. For example, 2016/679 on 1 May 2016.
   *
   * @return the days of the state. Empty if Cellar does not give the entry into force.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "not-in-force")
  public static Optional<Span> notInForce(Days days) {
    return days.entryIntoForce() == null
        ? Optional.empty()
        : Span.of(null, Span.earlier(days.entryIntoForce(), days.inForce().until()));
  }

  /**
   * After its end of validity, an act is no longer in force: a later act repealed it, or it
   * expired. Only an act on its own has this state. For example, 185/2010 from 15 November 2015.
   *
   * @return the days of the state. Empty if the act has no end of validity.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "no-longer-in-force")
  public static Optional<Span> noLongerInForce(Days days) {
    return days.endOfValidity() == null
        ? Optional.empty()
        : Optional.of(new Span(days.inForce().until(), null));
  }

  /**
   * The act is in force, and the day is on or after the last day from which it can apply. If it can
   * apply from one day, that day is the last day. If a clause sets one more day that Cellar does
   * not give, there is no last day. For example, 2016/679 from 25 May 2018.
   *
   * @return the days of the state. Empty if there is no last day.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "applies")
  public static Optional<Span> applies(Days days) {
    var inForce = days.inForce();
    return days.last() == null
        ? Optional.empty()
        : Span.of(Span.later(days.last(), inForce.from()), inForce.until());
  }

  /**
   * The act is in force, and the day is before the first day from which it can apply. For an act on
   * its own, no date for a part is on or before the day. For example, 2016/679 from 24 May 2016 to
   * 24 May 2018.
   *
   * @return the days of the state. Empty if the data gives no day from which it can apply.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "does-not-apply-yet")
  public static Optional<Span> doesNotApplyYet(Days days) {
    var inForce = days.inForce();
    return days.first() == null
        ? Optional.empty()
        : Span.of(
            inForce.from(), Span.earlier(Span.earlier(days.first(), days.part()), inForce.until()));
  }

  /**
   * The act is in force. A date for a part is on or before the day, and the day is before the day
   * for the whole act. Only an act on its own has this state. If the data does not tell the day for
   * the whole act, a date for a part does not give this state. For example, 2018/1862 from 27
   * December 2018 to 6 March 2023.
   *
   * @return the days of the state. Empty if no part applies before the whole act.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "applies-in-part")
  public static Optional<Span> appliesInPart(Days days) {
    var inForce = days.inForce();
    return days.part() == null || days.last() == null
        ? Optional.empty()
        : Span.of(
            Span.later(days.part(), inForce.from()), Span.earlier(days.last(), inForce.until()));
  }

  /**
   * The act is in force, and the data does not tell if it applies. The viewer does not know the
   * state, and says so. It does not give a state that it must guess. This is true in each of these
   * cases:
   *
   * <ul>
   *   <li>The data gives no day from which it can apply. For example, a clause sets the day by an
   *       event.
   *   <li>The day is on or after the first day from which it can apply, and before the last.
   *   <li>A clause sets one more day that Cellar does not give, and the day is on or after the
   *       first day.
   * </ul>
   *
   * @return the days of the state. Empty if the data tells the state on each day.
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "not-known")
  public static Optional<Span> notKnown(Days days) {
    var inForce = days.inForce();
    return days.first() == null
        ? Span.of(inForce.from(), inForce.until())
        : Span.of(
            Span.later(days.first(), inForce.from()), Span.earlier(days.last(), inForce.until()));
  }

  /**
   * An amending act stands as its changes stand. It applies in part if a change applies and a
   * change does not apply yet. An act for which Cellar lists no changed place counts as one change.
   *
   * @param changes the states of each change of the amending act
   * @param whole the states of the amending act as a whole, as its dates tell them
   * @return how the act stands, with the days on which it stands so
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "how-an-amending-act-stands")
  public static Periods<Stands> howAnAmendingActStands(
      List<Periods<LegalState>> changes, Periods<LegalState> whole) {
    var timelines = changes.isEmpty() ? List.of(whole) : changes;
    var starts = new TreeSet<LocalDate>();
    timelines.forEach(each -> starts.addAll(each.starts()));
    var first = starts.isEmpty() ? LocalDate.EPOCH : starts.first().minusDays(1);
    var stands = Periods.always(stands(timelines, first));
    starts.forEach(start -> stands.from(start, stands(timelines, start)));
    return stands.build();
  }

  private static Stands stands(List<Periods<LegalState>> timelines, LocalDate day) {
    var states = timelines.stream().map(each -> each.on(day).state()).toList();
    var applies = count(states, State.APPLIES);
    var notApplying = count(states, State.NOT_APPLYING);
    var notKnown = count(states, State.NOT_KNOWN);
    return new Stands(
        whole(states, applies, notApplying, notKnown), applies, notApplying, notKnown);
  }

  private static State whole(List<State> states, int applies, int notApplying, int notKnown) {
    if (states.contains(State.NOT_IN_FORCE)) {
      return State.NOT_IN_FORCE;
    }
    if (applies > 0 && notApplying > 0) {
      return State.IN_PART;
    }
    if (notKnown > 0) {
      return State.NOT_KNOWN;
    }
    return applies > 0 ? State.APPLIES : State.NOT_APPLYING;
  }

  private static int count(List<State> states, State state) {
    return (int) states.stream().filter(state::equals).count();
  }
}
