// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability.Applies;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability.Corrects;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed.NotApplying;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The feature "When a change applies": one function for each of its rules. A rule reads facts and
 * gives one new fact. It does not call another rule: {@link Applicability} and {@link Attributed}
 * apply the rules. The date itself comes from the dates of the act: the catalogue's rule
 * date-from-the-act.
 */
public final class WhenAChangeApplies {

  private WhenAChangeApplies() {}

  /**
   * Cellar can give a day for the change at the occurrence. That day can give the date only if it
   * is after the earlier text. A day that is not after it is stale: the earlier text does not have
   * the change, so the change does not apply from that day.
   *
   * @param amendments Cellar's amendments of the occurrence
   * @param amending the act that made the change
   * @param from the version of the earlier text
   * @return the days of Cellar for the change that are not stale
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "a-stale-day-of-cellar")
  public static Set<LocalDate> aStaleDayOfCellar(
      List<Amendment> amendments, ActIdentifier amending, Version from) {
    return amendments.stream()
        .filter(amendment -> amendment.act().equals(amending))
        .map(Amendment::from)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(from.date()))
        .collect(Collectors.toSet());
  }

  /**
   * The sidenote names the clause of the act that sets the date. The acts that amended that clause
   * moved the date, and the sidenote names them. A clause can set a day that Cellar does not know.
   * If Cellar names no such clause, the sidenote says nothing of when the change applies.
   *
   * @param amending the act that made the change
   * @param applying when the act applies, as its dates tell
   * @return when the change applies, with the clause and the acts that moved the date. Empty if a
   *     clause sets the day and Cellar names no such clause.
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "the-clause-of-the-date")
  public static Optional<Applicability> theClauseOfTheDate(
      ActIdentifier amending, Applying applying) {
    var provisions = new LinkedHashSet<ActDate.Provision>();
    var amendedBy = new LinkedHashSet<ActIdentifier>();
    var setBy = new LinkedHashSet<ActIdentifier>();
    for (var date : applying.dates()) {
      provisions.addAll(date.clause().provisions());
      amendedBy.addAll(date.clause().amendedBy());
      setBy.addAll(date.clause().setBy());
    }
    if (applying instanceof Applying.SetBy && provisions.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Applies(
            amending,
            applying,
            List.copyOf(provisions),
            List.copyOf(amendedBy),
            List.copyOf(setBy)));
  }

  /**
   * A corrigendum corrects its act from the start. The start is the earliest day from which the act
   * applies, or the entry into force if the act has no day of application. The sidenote also names
   * the first text between the two days that has the corrigendum.
   *
   * @param corrigendum the corrigendum
   * @param corrected the act that it corrects
   * @param datesOfTheCorrectedAct the dates of that act
   * @param firstIn the day of the first text between the two days that has the corrigendum. Null if
   *     none has it.
   */
  @BusinessRule(
      group = RuleGroup.WHEN_A_CHANGE_APPLIES,
      name = "corrigendum-corrects-from-the-start")
  public static Corrects corrigendumCorrectsFromTheStart(
      ActIdentifier corrigendum,
      ActIdentifier corrected,
      List<ActDate> datesOfTheCorrectedAct,
      @Nullable LocalDate firstIn) {
    var dated = datesOfTheCorrectedAct.stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    var start =
        (applying.isEmpty() ? dated : applying)
            .stream().map(ActDate::date).filter(Objects::nonNull).min(Comparator.naturalOrder());
    return new Corrects(corrigendum, corrected, start.orElse(null), firstIn);
  }

  /**
   * A text can include the change of an act before the change applies. On a day before the change
   * applies, a difference says so if its occurrence has such a change. The change is at the
   * occurrence of the difference, at an occurrence that it is in, or at an occurrence in it. The
   * difference names the act and the day.
   *
   * @param act the act of a change at the occurrence that does not apply on the day yet. Null if
   *     each change there applies.
   * @param day the day
   * @return the change that does not apply yet, or empty if each change applies
   */
  @BusinessRule(group = RuleGroup.WHEN_A_CHANGE_APPLIES, name = "change-that-does-not-apply-yet")
  public static Optional<NotApplying> changeThatDoesNotApplyYet(
      @Nullable ActIdentifier act, LocalDate day) {
    return Optional.ofNullable(act).map(each -> new NotApplying(each, day));
  }
}
