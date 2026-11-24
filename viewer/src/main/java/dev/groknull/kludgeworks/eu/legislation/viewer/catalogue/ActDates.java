// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The dates of an act, and what they tell: from when the act, an act that its versions integrate or
 * an act that amends it applies, and its state on a day. They are Cellar's typed dates. The rules
 * are in the features "When an act applies" and "The state on a day".
 *
 * @param id the act's CELEX
 * @param own its own dates: when it enters into force, applies or takes effect
 * @param amending the own dates of each act that its versions integrate or that amends it, by act.
 *     An act that amends it can be in no version yet. An act that Cellar has no dates for is left
 *     out.
 */
@ValueObject
public record ActDates(
    ActIdentifier id, List<ActDate> own, Map<ActIdentifier, List<ActDate>> amending) {
  /**
   * The kinds of date that tell from when a whole act applies, in the order in which {@link
   * #appliesFrom} looks for them.
   */
  private static final List<Set<ActDate.Kind>> WHOLE_ACT =
      List.of(
          EnumSet.of(ActDate.Kind.APPLICATION, ActDate.Kind.TAKING_EFFECT),
          EnumSet.of(ActDate.Kind.TRANSPOSITION),
          EnumSet.of(ActDate.Kind.ENTRY_INTO_FORCE));

  public ActDates {
    own = List.copyOf(own);
    amending =
        amending.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /**
   * Returns the own dates of {@code act}, if it is this act, an act that its versions integrate or
   * an act that amends it. For any other act, returns an empty list.
   */
  public List<ActDate> datesOf(ActIdentifier act) {
    return act.equals(id) ? own : amending.getOrDefault(act, List.of());
  }

  /**
   * Returns the date from which the whole of {@code act} applies. {@code act} is the act, an act
   * that its versions integrate or an act that amends it.
   *
   * <ul>
   *   <li>It is the act's date of application or of taking effect that is not for a part.
   *   <li>A directive that has no such date applies from the day from which the Member States apply
   *       it. For example, 2019/878 enters into force on 27 June 2019, and the Member States apply
   *       it from 29 December 2020.
   *   <li>An act that has neither date applies from its entry into force. A directive does not: the
   *       Member States apply it through their own measures, so its entry into force does not tell
   *       from when it applies. For example, 2015/2366 enters into force on 12 January 2016, and
   *       the Member States apply it from 13 January 2018. Cellar has that day only as the deadline
   *       for transposition, and that deadline is not always the day from which they apply it.
   *   <li>A date for a part is an exception for that part. It does not move the date of the whole
   *       act. For example, 2021/255 enters into force on 11 March 2021, and only four of its
   *       points apply from later dates. So it applies from 11 March 2021.
   * </ul>
   *
   * <p>Returns empty if Cellar does not know the date. For example, 2019/413 applies from a date
   * that its Article 3 sets by an event. It also returns empty for a directive that the Member
   * States apply from more than one day, because Cellar does not tell which day is for the whole
   * directive. For example, they apply one point of 2019/2034 from 26 March 2020, and the rest from
   * 26 June 2021.
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "whole-act-applies-from")
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "date-for-a-part")
  public Optional<ActDate> appliesFrom(ActIdentifier act) {
    var whole = datesOf(act).stream().filter(date -> !date.forPart()).toList();
    for (var kinds : WHOLE_ACT) {
      if (act.directive() && kinds.contains(ActDate.Kind.ENTRY_INTO_FORCE)) {
        return Optional.empty();
      }
      var dates = whole.stream().filter(date -> kinds.contains(date.kind())).toList();
      if (dates.stream().anyMatch(date -> date.date() == null)
          || (kinds.contains(ActDate.Kind.TRANSPOSITION) && dates.size() > 1)) {
        return Optional.empty();
      }
      if (!dates.isEmpty()) {
        return dates.stream()
            .min(Comparator.comparing(date -> Objects.requireNonNull(date.date())));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns whether a provision of {@code act} can apply from {@code day}. It can if {@code day} is
   * one of the dates of the act. It cannot if {@code day} is only its entry into force, and the
   * whole act applies from another day or from a day that Cellar does not know. {@code act} is this
   * act, an act that its versions integrate or an act that amends it.
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "a-day-the-act-can-apply-from")
  public boolean appliesFromDay(ActIdentifier act, LocalDate day) {
    var on = datesOf(act).stream().filter(date -> day.equals(date.date())).toList();
    if (on.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)) {
      return true;
    }
    return !on.isEmpty() && appliesFrom(act).map(ActDate::date).filter(day::equals).isPresent();
  }

  /**
   * Returns when {@code act} applies, as its dates tell. {@code act} is the act, an act that its
   * versions integrate or an act that amends it.
   *
   * <ol>
   *   <li>If Cellar has no day for any date of application, the act applies from a day that its
   *       clauses set.
   *   <li>If Cellar gives one day for the provision, in {@code placeDays}, and the provision can
   *       apply from it ({@link #appliesFromDay}), the act applies from that day.
   *   <li>If it applies from one day, and not in part, it applies from {@link #appliesFrom}. A
   *       directive that has only an entry into force is in force, and Cellar does not tell from
   *       when it applies.
   *   <li>Otherwise it applies from more than one day, one on each day of its dates.
   * </ol>
   *
   * <p>Returns empty if Cellar has no dates of the act.
   *
   * @param placeDays the days that Cellar gives for the provision, from its amendments
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "entry-into-force-only")
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "cellar-day-else-the-parts")
  public Optional<Applying> applying(ActIdentifier act, Set<LocalDate> placeDays) {
    var all = datesOf(act);
    if (all.isEmpty()) {
      return Optional.empty();
    }
    var dated = all.stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    if (dated.isEmpty()
        || (applying.isEmpty()
            && all.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE))) {
      return Optional.of(
          new Applying.SetBy(all.stream().filter(date -> date.date() == null).toList()));
    }
    if (placeDays.size() == 1) {
      var day = placeDays.iterator().next();
      if (appliesFromDay(act, day)) {
        var on = on(dated, day);
        return Optional.of(new Applying.From(day, onlyEntryIntoForce(on), on));
      }
    }
    var used = applying.isEmpty() ? dated : applying;
    if (days(used).size() == 1 && applying.stream().noneMatch(ActDate::forPart)) {
      var whole = appliesFrom(act).map(ActDate::date);
      if (whole.isPresent()) {
        return Optional.of(new Applying.From(whole.get(), applying.isEmpty(), used));
      }
      if (applying.isEmpty()) {
        return Optional.of(new Applying.InForce(days(dated).getFirst(), dated));
      }
    }
    var whole = appliesFrom(act).map(ActDate::date);
    var parts =
        days(dated).stream()
            .map(
                day -> {
                  var on = on(dated, day);
                  return new Applying.Start(
                      day, onlyEntryIntoForce(on), on.stream().allMatch(ActDate::forPart));
                })
            .toList();
    var first =
        parts.stream()
            .filter(part -> !part.entryIntoForce() || whole.filter(part.day()::equals).isPresent())
            .findFirst()
            .orElseGet(parts::getFirst)
            .day();
    return Optional.of(new Applying.FromMoreThanOneDay(parts, first, dated));
  }

  /**
   * Returns the day on which {@code act} enters into force, or empty if Cellar does not give it.
   * {@code act} is the act, an act that its versions integrate or an act that amends it.
   */
  public Optional<LocalDate> entryIntoForce(ActIdentifier act) {
    return datesOf(act).stream()
        .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder());
  }

  /**
   * Returns the day from which a change of {@code amending} applies, if the data gives one day for
   * it: {@link #applying} gives a day, and not parts. {@code placeDays} are the days that Cellar
   * gives for the place of the change. Returns empty if the data does not tell the part of the
   * change.
   */
  public Optional<LocalDate> changeAppliesFrom(ActIdentifier amending, Set<LocalDate> placeDays) {
    return applying(amending, placeDays)
        .flatMap(
            applying ->
                switch (applying) {
                  case Applying.From from -> Optional.of(from.day());
                  case Applying.FromMoreThanOneDay _, Applying.SetBy _, Applying.InForce _ ->
                      Optional.empty();
                });
  }

  /**
   * Returns the state on {@code day} of {@code act}, or of one change that it makes. {@code act} is
   * the act, an act that its versions integrate or an act that amends it. {@code placeDays} are the
   * days that Cellar gives for the place of the change. They are empty for the act as a whole.
   *
   * <ol>
   *   <li>Before the entry into force of the act, it is not in force.
   *   <li>If it applies from one day ({@link #applying}), it applies from that day, and does not
   *       apply yet before it.
   *   <li>If it applies from more than one day, it does not apply yet before the first part. It
   *       applies from the last part, unless a clause sets one more day that Cellar does not know.
   *       Between them, Cellar does not tell which part the change is in, so the state is not
   *       known.
   *   <li>In each other case the state is not known. For example, a clause sets the day by an
   *       event, or Cellar has no dates of the act.
   * </ol>
   */
  @BusinessRule(group = RuleGroup.THE_STATE_ON_A_DAY, name = "state-on-a-day")
  public State stateOn(ActIdentifier act, Set<LocalDate> placeDays, LocalDate day) {
    if (entryIntoForce(act).filter(day::isBefore).isPresent()) {
      return State.NOT_IN_FORCE;
    }
    var openDay =
        datesOf(act).stream()
            .anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null);
    return applying(act, placeDays)
        .map(
            applying ->
                switch (applying) {
                  case Applying.From from ->
                      day.isBefore(from.day()) ? State.NOT_APPLYING : State.APPLIES;
                  case Applying.FromMoreThanOneDay parts -> {
                    if (day.isBefore(parts.first())) {
                      yield State.NOT_APPLYING;
                    }
                    yield !openDay && !day.isBefore(parts.starts().getLast().day())
                        ? State.APPLIES
                        : State.NOT_KNOWN;
                  }
                  case Applying.SetBy _, Applying.InForce _ -> State.NOT_KNOWN;
                })
        .orElse(State.NOT_KNOWN);
  }

  /** Returns the days that Cellar gives for the place of {@code change}: one day, or none. */
  public static Set<LocalDate> days(Amendment change) {
    return change.from() == null ? Set.of() : Set.of(change.from());
  }

  /** Returns the dates among {@code dates} that fall on {@code day}. */
  private static List<ActDate> on(List<ActDate> dates, LocalDate day) {
    return dates.stream().filter(date -> day.equals(date.date())).toList();
  }

  /** Returns whether each of {@code dates} is an entry into force. */
  private static boolean onlyEntryIntoForce(List<ActDate> dates) {
    return dates.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE);
  }

  /** Returns the days of {@code dates}, without repeats, in order. */
  private static List<LocalDate> days(List<ActDate> dates) {
    return dates.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
  }

  /**
   * Returns the day from which {@code version} first applies, if that is after the day on which it
   * starts. Only the act as published can apply later: it starts on its entry into force, and can
   * apply from a later day. That day is its earliest date after the version starts that is not its
   * entry into force. For example, 2016/679 enters into force on 24 May 2016 and applies from 25
   * May 2018. Returns empty for a consolidated version, and for an act that applies from its entry
   * into force.
   */
  public Optional<LocalDate> appliesLater(Version version) {
    if (version.kind() != Version.Kind.ORIGINAL) {
      return Optional.empty();
    }
    return own.stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(version.date()))
        .min(Comparator.naturalOrder());
  }

  /**
   * Returns whether the act applies only in part from {@code day}: each of its dates on that day is
   * for a part. For example, 2024/1689 applies in part from 2 February 2025.
   */
  public boolean inPartFrom(LocalDate day) {
    return own.stream().filter(date -> day.equals(date.date())).allMatch(ActDate::forPart);
  }

  /**
   * Returns the day from which the whole of the act applies, {@link #appliesFrom}, if that is after
   * {@code day}. For example, 2024/1689 applies in part from 2 February 2025, and in full from 2
   * August 2026.
   */
  public Optional<LocalDate> inFullAfter(LocalDate day) {
    return appliesFrom(id).map(ActDate::date).filter(from -> from.isAfter(day));
  }

  /**
   * Returns whether {@code act} applies in part on {@code day}: a date for a part of it falls on or
   * before that day. {@code act} is the act, an act that its versions integrate or an act that
   * amends it.
   */
  public boolean appliesInPartOn(ActIdentifier act, LocalDate day) {
    return datesOf(act).stream()
        .anyMatch(date -> date.forPart() && date.date() != null && !date.date().isAfter(day));
  }

  /**
   * Returns the provisions that set a day from which the act applies, where Cellar does not know
   * the day. For example, a day that an article sets by an event.
   */
  public List<ActDate.Provision> unknownDays() {
    return own.stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE && date.date() == null)
        .flatMap(date -> date.clause().provisions().stream())
        .distinct()
        .toList();
  }
}
