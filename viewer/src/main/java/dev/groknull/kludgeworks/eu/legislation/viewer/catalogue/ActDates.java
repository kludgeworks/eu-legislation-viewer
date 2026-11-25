// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.Comparator;
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
   * it: {@link DatedAct#applying} gives a day, and not parts. {@code placeDays} are the days that
   * Cellar gives for the place of the change. Returns empty if the data does not tell the part of
   * the change.
   */
  public Optional<LocalDate> changeAppliesFrom(ActIdentifier amending, Set<LocalDate> placeDays) {
    return DatedAct.of(this, amending)
        .applying(placeDays)
        .flatMap(applying ->
            switch (applying) {
              case Applying.From from -> Optional.of(from.day());
              case Applying.FromMoreThanOneDay _, Applying.SetBy _, Applying.InForce _ ->
                  Optional.empty();
            });
  }

  /** Returns the days that Cellar gives for the place of {@code change}: one day, or none. */
  public static Set<LocalDate> days(Amendment change) {
    return change.from() == null ? Set.of() : Set.of(change.from());
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
   * Returns the day from which the whole of the act applies, {@link
   * WhenAnActApplies#wholeActAppliesFrom}, if that is after {@code day}. For example, 2024/1689
   * applies in part from 2 February 2025, and in full from 2 August 2026.
   */
  public Optional<LocalDate> inFullAfter(LocalDate day) {
    return DatedAct.of(this, id)
        .appliesFrom()
        .from()
        .map(ActDate::date)
        .filter(from -> from.isAfter(day));
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
