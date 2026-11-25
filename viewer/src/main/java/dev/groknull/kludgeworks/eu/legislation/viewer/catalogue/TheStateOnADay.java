// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The state on a day": one function for each of its rules. A rule reads facts and
 * gives one new fact. Its answer has the state, and the facts that give it: a page puts them into
 * words, and does not look them up again.
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
   * The state of an act, or of one change, on a day: no longer in force, not in force, in force and
   * does not apply yet, or applies. Else the data does not tell.
   *
   * <ol>
   *   <li>Before the entry into force of the act, it is not in force.
   *   <li>If it applies from one day, it applies from that day, and does not apply yet before it.
   *   <li>If it applies from more than one day, it does not apply yet before the first part. It
   *       applies from the last part, unless a clause sets one more day that Cellar does not know.
   *       Between them, Cellar does not tell which part the change is in, so the state is not
   *       known.
   *   <li>In each other case the state is not known. For example, a clause sets the day by an
   *       event, or Cellar has no dates of the act.
   *   <li>After its end of validity, an act is no longer in force: a later act repealed it, or it
   *       expired. Only an act on its own has this state.
   * </ol>
   *
   * @param applying when the act, or the change, applies. Null if Cellar has no dates of the act.
   * @param dates the dates of the act
   * @param endOfValidity the end of validity of the act. Null if it has none, or for a change.
   * @param repealedBy the acts that repeal the act
   * @return the states, with the days of each
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "state-on-a-day")
  public static Periods<LegalState> stateOnADay(
      @Nullable Applying applying,
      List<ActDate> dates,
      @Nullable LocalDate endOfValidity,
      List<ActIdentifier> repealedBy) {
    Periods.Builder<LegalState> states;
    if (applying == null) {
      states = Periods.always(new LegalState.NotKnown(LegalState.Why.NO_DATES, null));
    } else {
      states =
          switch (applying) {
            case Applying.From from ->
                Periods.<LegalState>always(new LegalState.NotApplying(applying))
                    .from(from.day(), new LegalState.Applies(applying));
            case Applying.FromMoreThanOneDay parts -> {
              var open =
                  dates.stream()
                      .anyMatch(date ->
                          date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null);
              var known =
                  Periods.<LegalState>always(new LegalState.NotApplying(applying))
                      .from(
                          parts.first(),
                          new LegalState.NotKnown(
                              open ? LegalState.Why.ONE_MORE_DAY : LegalState.Why.BETWEEN_THE_PARTS,
                              applying));
              yield open
                  ? known
                  : known.from(parts.starts().getLast().day(), new LegalState.Applies(applying));
            }
            case Applying.SetBy _ ->
                Periods.always(
                    new LegalState.NotKnown(LegalState.Why.A_CLAUSE_SETS_THE_DAY, applying));
            case Applying.InForce _ ->
                Periods.always(new LegalState.NotKnown(LegalState.Why.IN_FORCE_ONLY, applying));
          };
    }
    var force =
        dates.stream()
            .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
            .map(ActDate::date)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());
    if (force.isPresent()) {
      states.before(force.get(), new LegalState.NotInForce(force.get()));
    }
    if (endOfValidity != null) {
      states.from(
          endOfValidity.plusDays(1), new LegalState.NoLongerInForce(endOfValidity, repealedBy));
    }
    return states.build();
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
