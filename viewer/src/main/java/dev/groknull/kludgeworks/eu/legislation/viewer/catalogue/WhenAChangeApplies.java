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
 * The rule of the feature "When a change applies" (comparison) that tells when an act, or a change
 * at one place, applies. The state on a day needs it too. It is one function of facts. The other
 * rules of the feature need a comparison, and are not here.
 */
public final class WhenAChangeApplies {

  private WhenAChangeApplies() {}

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
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "date-from-the-act", revision = 2)
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
