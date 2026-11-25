// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.WholeAct;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Two rules of the feature "When a change applies" (comparison), which the state on a day needs:
 * when an act, or a change at one place, applies. Each is one function of facts. {@link DatedAct}
 * applies them in order. The other rules of the feature need a comparison, and are not here.
 */
public final class WhenAChangeApplies {

  private WhenAChangeApplies() {}

  /**
   * An act whose only date is its entry into force applies from that day. A directive is only in
   * force from it: the Member States apply it through their own measures.
   *
   * @param dates the dates of the act
   * @param whole from which day the whole act applies
   * @return when the act applies, or empty if the rule is not for this act
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "entry-into-force-only")
  public static Optional<Applying> entryIntoForceOnly(List<ActDate> dates, WholeAct whole) {
    var days = days(dates);
    if (dates.isEmpty()
        || days.size() != 1
        || !dates.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)) {
      return Optional.empty();
    }
    var dated = dates.stream().filter(date -> date.date() != null).toList();
    return Optional.of(
        whole
            .from()
            .<Applying>map(
                from -> new Applying.From(Objects.requireNonNull(from.date()), true, dated))
            .orElseGet(() -> new Applying.InForce(days.getFirst(), dated)));
  }

  /**
   * Cellar's day for the place chooses the date, if the act can apply from it. Else the act applies
   * from more than one day. If Cellar has no day for a date of application, a clause sets the day.
   *
   * <ol>
   *   <li>If Cellar has no day for any date of application, the act applies from a day that its
   *       clauses set.
   *   <li>If Cellar gives one day for the place, and a provision of the act can apply from it, the
   *       act applies from that day.
   *   <li>If the act applies from one day, and not in part, it applies from the day from which the
   *       whole act applies.
   *   <li>Else it applies from more than one day: one for each day of its dates.
   * </ol>
   *
   * @param dates the dates of the act
   * @param whole from which day the whole act applies
   * @param canApplyFrom the days that a provision of the act can apply from
   * @param placeDays the days that Cellar gives for the place. They are empty for the act as a
   *     whole.
   * @return when the act applies at the place, or empty if Cellar has no dates of the act
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "cellar-day-else-the-parts")
  public static Optional<Applying> cellarDayElseTheParts(
      List<ActDate> dates, WholeAct whole, Set<LocalDate> canApplyFrom, Set<LocalDate> placeDays) {
    if (dates.isEmpty()) {
      return Optional.empty();
    }
    var dated = dates.stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    if (dated.isEmpty()
        || (applying.isEmpty()
            && dates.stream().anyMatch(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE))) {
      return Optional.of(
          new Applying.SetBy(dates.stream().filter(date -> date.date() == null).toList()));
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
    var wholeDay = whole.from().map(ActDate::date);
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
