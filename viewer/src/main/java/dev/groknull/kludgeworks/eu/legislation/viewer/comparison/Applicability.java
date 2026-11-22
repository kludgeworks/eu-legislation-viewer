// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * From when a change that an act made to a difference applies, as the sidenote of the difference
 * dates it. ELI calls this the date of applicability. The date comes from the dates of the act that
 * made the change, never from the date of a version (R-WHO-15).
 */
@ValueObject
public sealed interface Applicability {

  /** Returns the act that made the change. */
  ActIdentifier act();

  /**
   * An amending act's change applies as the act's dates tell ({@link Act#applying}).
   *
   * @param act the amending act
   * @param applying when it applies: from one day, in parts, from a day that a clause sets, or in
   *     force from a day
   * @param clause the provisions of the act that set its dates. Empty where Cellar does not say.
   * @param amendedBy the acts that changed those provisions since: they moved the date (R-WHO-19)
   */
  record Applies(
      ActIdentifier act,
      Applying applying,
      List<ActDate.Provision> clause,
      List<ActIdentifier> amendedBy)
      implements Applicability {
    public Applies {
      clause = List.copyOf(clause);
      amendedBy = List.copyOf(amendedBy);
    }
  }

  /**
   * A corrigendum corrects its act from the start (R-WHO-20).
   *
   * @param act the corrigendum
   * @param corrected the act that it corrects
   * @param from the earliest day from which the corrected act applies, else its entry into force.
   *     Null where the act has no dated day.
   * @param firstIn the day of the first text between the two days of the comparison that has the
   *     corrigendum. Null where none has it.
   */
  record Corrects(
      ActIdentifier act,
      ActIdentifier corrected,
      @Nullable LocalDate from,
      @Nullable LocalDate firstIn)
      implements Applicability {}

  /**
   * Returns when the change that {@code each} made to a difference applies, in the comparison of
   * {@code from} with {@code to}. A corrigendum corrects from the start (R-WHO-20). For an amending
   * act, the days that may choose its date are those of {@link #daysBetween} if a part of it
   * applies in the step ({@code part}), else those of {@link #cellarDays}. Returns empty where
   * nothing can be said (R-WHO-18).
   *
   * @param amendments Cellar's amendments of the occurrence of the difference
   */
  static Optional<Applicability> of(
      Act act,
      Inclusions included,
      Version from,
      Version to,
      ActIdentifier each,
      List<Amendment> amendments,
      boolean part) {
    if (each.corrigendum()) {
      return Optional.of(corrects(act, included, from, to, each));
    }
    return of(
        act, each, part ? daysBetween(act, each, from, to) : cellarDays(amendments, each, from));
  }

  /**
   * Returns when {@code amending} applies, from its dates in {@code act} ({@link Act#applying}).
   * {@code days} are the days that may choose the date among the act's dates (R-WHO-16). If none
   * chooses one, the act applies in parts (R-WHO-17). Returns empty where Cellar has no dates of
   * the act, or where the act applies from a day that no clause that Cellar names sets (R-WHO-18).
   */
  static Optional<Applicability> of(Act act, ActIdentifier amending, Set<LocalDate> days) {
    return act.applying(amending, days)
        .flatMap(
            applying -> {
              var provisions = new LinkedHashSet<ActDate.Provision>();
              var amendedBy = new LinkedHashSet<ActIdentifier>();
              for (var date : applying.dates()) {
                provisions.addAll(date.clause().provisions());
                amendedBy.addAll(date.clause().amendedBy());
              }
              if (applying instanceof Applying.SetBy && provisions.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(
                  new Applies(amending, applying, List.copyOf(provisions), List.copyOf(amendedBy)));
            });
  }

  /**
   * Returns the days that Cellar's amendments of the occurrence by {@code amending} have, after
   * {@code from} (R-WHO-16). A day that is not after the earlier text is stale: the earlier text
   * does not have the change, so the change does not apply from that day.
   */
  static Set<LocalDate> cellarDays(
      List<Amendment> amendments, ActIdentifier amending, Version from) {
    return amendments.stream()
        .filter(amendment -> amendment.act().equals(amending))
        .map(Amendment::from)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(from.date()))
        .collect(Collectors.toSet());
  }

  /** Returns the days of {@code amending}'s dates after {@code from}, up to {@code to}. */
  static Set<LocalDate> daysBetween(Act act, ActIdentifier amending, Version from, Version to) {
    return act.datesOf(amending).stream()
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(from.date()) && !day.isAfter(to.date()))
        .collect(Collectors.toSet());
  }

  /**
   * Returns how {@code corrigendum} corrects its act (R-WHO-20): from the earliest day from which
   * the act applies, else from its entry into force, and first in the first text after {@code
   * from}, up to {@code to}, that has it.
   */
  static Corrects corrects(
      Act act, Inclusions included, Version from, Version to, ActIdentifier corrigendum) {
    var corrected = corrigendum.corrected().orElseThrow();
    var dated = act.datesOf(corrected).stream().filter(date -> date.date() != null).toList();
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    var start =
        (applying.isEmpty() ? dated : applying)
            .stream().map(ActDate::date).filter(Objects::nonNull).min(Comparator.naturalOrder());
    return new Corrects(
        corrigendum,
        corrected,
        start.orElse(null),
        firstIncluding(included, from, to, corrigendum).map(Version::date).orElse(null));
  }

  /**
   * Returns the first version after {@code from}, up to {@code to}, that includes {@code amending}.
   */
  static Optional<Version> firstIncluding(
      Inclusions included, Version from, Version to, ActIdentifier amending) {
    return included.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> included.includes(v, amending))
        .findFirst();
  }
}
