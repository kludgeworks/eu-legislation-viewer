// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The feature "When an act applies": one function for each of its rules, in the order of the
 * feature. A rule reads facts and gives one new fact. It does not call another rule: {@link
 * DatedAct} applies the rules in order. The rule a-later-act-amended-or-set-the-date is applied
 * when the dates are read from Cellar's comments.
 */
public final class WhenAnActApplies {

  /** The kinds of date that tell from when a whole act applies, in order. */
  private static final List<Set<ActDate.Kind>> KINDS =
      List.of(
          EnumSet.of(ActDate.Kind.APPLICATION, ActDate.Kind.TAKING_EFFECT),
          EnumSet.of(ActDate.Kind.TRANSPOSITION),
          EnumSet.of(ActDate.Kind.ENTRY_INTO_FORCE));

  private WhenAnActApplies() {}

  /** From which day a whole act applies, with the dates that tell it. */
  public sealed interface WholeAct {

    /** Returns the date from which the whole act applies, or empty if the data does not tell. */
    default Optional<ActDate> from() {
      return this instanceof From from ? Optional.of(from.date()) : Optional.empty();
    }

    /** The whole act applies from {@code date}. The date has the clause that sets it. */
    record From(ActDate date) implements WholeAct {}

    /**
     * The data does not tell from which day the whole act applies.
     *
     * @param why the reason
     * @param dates the dates that show it: the placeholder, or the days of the Member States
     */
    record NotKnown(Why why, List<ActDate> dates) implements WholeAct {
      public NotKnown {
        dates = List.copyOf(dates);
      }
    }

    /** Why the data does not tell from which day a whole act applies. */
    enum Why {
      /** A directive has only its entry into force: the Member States apply it. */
      DIRECTIVE_WITH_ONLY_ITS_ENTRY_INTO_FORCE,
      /** Cellar has a placeholder for the date. */
      PLACEHOLDER,
      /** The Member States apply the directive from more than one day. */
      MORE_THAN_ONE_DAY,
      /** Cellar gives no date for the whole act. */
      NO_DATE
    }
  }

  /**
   * The later acts that Cellar names for a date of an act.
   *
   * @param amendedBy the acts that amended the date
   * @param setBy the acts that set a day that the clause left to them
   */
  public record LaterActs(List<ActIdentifier> amendedBy, List<ActIdentifier> setBy) {
    public LaterActs {
      amendedBy = List.copyOf(amendedBy);
      setBy = List.copyOf(setBy);
    }
  }

  /**
   * A later act that Cellar names for a date amended the date, if it is one of the acts that amend
   * the act of the date. For example, 2020/910 replaces "31 December 2020" in Article 2 of
   * 2019/103. Else it set a day that the clause left to it: Decision 2023/201 sets the day from
   * which 2018/1862 applies. Cellar's comment is the same for the two.
   *
   * @param named the later acts that Cellar's comment on the date names, in its order
   * @param amending the acts that amend the act of the date
   */
  @BusinessRule(
      group = RuleGroup.WHEN_AN_ACT_APPLIES,
      name = "a-later-act-amended-or-set-the-date",
      revision = 2)
  public static LaterActs aLaterActAmendedOrSetTheDate(
      List<ActIdentifier> named, Set<ActIdentifier> amending) {
    return new LaterActs(
        named.stream().filter(amending::contains).toList(),
        named.stream().filter(each -> !amending.contains(each)).toList());
  }

  /**
   * A date for a part is an exception for that part only: it is not a date of the whole act. For
   * example, 2021/255 enters into force on 11 March 2021, and only four of its points apply from
   * later dates. So it applies from 11 March 2021.
   *
   * @param dates the dates of the act
   * @return the dates that are for the whole act
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "date-for-a-part")
  public static List<ActDate> dateForAPart(List<ActDate> dates) {
    return dates.stream().filter(date -> !date.forPart()).toList();
  }

  /**
   * An act applies from its date of application, or of taking effect. Else a directive applies from
   * the one day from which the Member States apply it. Else an act that is not a directive applies
   * from its entry into force.
   *
   * <ul>
   *   <li>A directive that has no date of application applies from the day from which the Member
   *       States apply it. For example, 2019/878 enters into force on 27 June 2019, and the Member
   *       States apply it from 29 December 2020.
   *   <li>A directive does not apply from its entry into force: the Member States apply it through
   *       their own measures. For example, 2015/2366 enters into force on 12 January 2016, and the
   *       Member States apply it from 13 January 2018. Cellar has that day only as the deadline for
   *       transposition, and that deadline is not always the day from which they apply it.
   *   <li>The data does not tell the day if Cellar does not know it. For example, 2019/413 applies
   *       from a date that its Article 3 sets by an event.
   *   <li>The data does not tell the day for a directive that the Member States apply from more
   *       than one day, because Cellar does not tell which day is for the whole directive. For
   *       example, they apply one point of 2019/2034 from 26 March 2020, and the rest from 26 June
   *       2021.
   * </ul>
   *
   * @param forTheWholeAct the dates of the act that are for the whole act
   * @param directive whether the act is a directive
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "whole-act-applies-from")
  public static WholeAct wholeActAppliesFrom(List<ActDate> forTheWholeAct, boolean directive) {
    for (var kinds : KINDS) {
      if (directive && kinds.contains(ActDate.Kind.ENTRY_INTO_FORCE)) {
        return new WholeAct.NotKnown(
            WholeAct.Why.DIRECTIVE_WITH_ONLY_ITS_ENTRY_INTO_FORCE, forTheWholeAct);
      }
      var found = forTheWholeAct.stream().filter(date -> kinds.contains(date.kind())).toList();
      if (found.stream().anyMatch(date -> date.date() == null)) {
        return new WholeAct.NotKnown(WholeAct.Why.PLACEHOLDER, found);
      }
      if (kinds.contains(ActDate.Kind.TRANSPOSITION) && found.size() > 1) {
        return new WholeAct.NotKnown(WholeAct.Why.MORE_THAN_ONE_DAY, found);
      }
      var first =
          found.stream().min(Comparator.comparing(date -> Objects.requireNonNull(date.date())));
      if (first.isPresent()) {
        return new WholeAct.From(first.get());
      }
    }
    return new WholeAct.NotKnown(WholeAct.Why.NO_DATE, List.of());
  }

  /**
   * A provision applies only from a day that the act can apply from: one of its dates. Its entry
   * into force alone does not count if the whole act applies from another day, or from a day that
   * Cellar does not know.
   *
   * @param dates the dates of the act
   * @param whole from which day the whole act applies
   * @return the days that a provision of the act can apply from
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "a-day-the-act-can-apply-from")
  public static Set<LocalDate> aDayTheActCanApplyFrom(List<ActDate> dates, WholeAct whole) {
    var wholeDay = whole.from().map(ActDate::date);
    return dates.stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day ->
            wholeDay.filter(day::equals).isPresent()
                || dates.stream()
                    .anyMatch(date ->
                        day.equals(date.date()) && date.kind() != ActDate.Kind.ENTRY_INTO_FORCE))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The date of a change comes from the dates of the act that made it. The first of these cases
   * that is true for the act gives the date:
   *
   * <ol>
   *   <li>Cellar has no day for the act's application. A clause of the act sets the day.
   *   <li>The only date of the act is its entry into force. The act applies from that day. A
   *       directive is only in force from it: the Member States apply it through their own
   *       measures.
   *   <li>The place has one day for the change, and a provision of the act can apply from that day.
   *       Cellar gives the day, or it is the date of the part that the act is named for. The change
   *       applies from that day.
   *   <li>The act has one day of application, and that day is for the whole act. The change applies
   *       from that day.
   *   <li>No other case is true. The act applies from more than one day: one for each day of its
   *       dates.
   * </ol>
   *
   * @param dates the dates of the act
   * @param whole from which day the whole act applies
   * @param canApplyFrom the days that a provision of the act can apply from
   * @param placeDays the days for the change at the place. They are empty for the act as a whole.
   * @return when the act applies at the place, or empty if Cellar has no dates of the act
   */
  @BusinessRule(group = RuleGroup.WHEN_AN_ACT_APPLIES, name = "date-from-the-act", revision = 2)
  public static Optional<Applying> dateFromTheAct(
      List<ActDate> dates, WholeAct whole, Set<LocalDate> canApplyFrom, Set<LocalDate> placeDays) {
    if (dates.isEmpty()) {
      return Optional.empty();
    }
    var dated = dates.stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    var wholeDay = whole.from().map(ActDate::date);
    if (dated.isEmpty()
        || (applying.isEmpty()
            && dates.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE))) {
      return Optional.of(
          new Applying.SetBy(dates.stream().filter(date -> date.date() == null).toList()));
    }
    if (applying.isEmpty() && days(dated).size() == 1) {
      return Optional.of(
          wholeDay
              .<Applying>map(day -> new Applying.From(day, true, dated))
              .orElseGet(() -> new Applying.InForce(days(dated).getFirst(), dated)));
    }
    if (placeDays.size() == 1) {
      var day = placeDays.iterator().next();
      if (canApplyFrom.contains(day)) {
        var on = on(dated, day);
        return Optional.of(
            new Applying.From(
                day,
                on.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE),
                on));
      }
    }
    if (days(applying).size() == 1
        && applying.stream().noneMatch(ActDate::forPart)
        && wholeDay.isPresent()) {
      return Optional.of(new Applying.From(wholeDay.get(), false, applying));
    }
    var parts =
        days(dated).stream()
            .map(day -> {
              var on = on(dated, day);
              return new Applying.Start(
                  day,
                  on.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE),
                  on.stream().allMatch(ActDate::forPart));
            })
            .toList();
    var first =
        parts.stream()
            .filter(
                part -> !part.entryIntoForce() || wholeDay.filter(part.day()::equals).isPresent())
            .findFirst()
            .orElseGet(parts::getFirst)
            .day();
    return Optional.of(new Applying.FromMoreThanOneDay(parts, first, dated));
  }

  private static List<ActDate> on(List<ActDate> dates, LocalDate day) {
    return dates.stream().filter(date -> day.equals(date.date())).toList();
  }

  private static List<LocalDate> days(List<ActDate> dates) {
    return dates.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
  }
}
