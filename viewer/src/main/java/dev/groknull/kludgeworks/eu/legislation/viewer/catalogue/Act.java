// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * An act: a work in Cellar, identified by its CELEX and its ELI, with its timeline.
 *
 * @param id the act's CELEX
 * @param eli its ELI; older acts may have none
 * @param type its resource type (REG_IMPL, DIR, DEC)
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply; none while it is open-ended
 * @param timeline its versions
 * @param dates its own dates: when it enters into force, applies or takes effect
 * @param amendingDates the own dates of each act its versions integrate, by the act; an act Cellar
 *     has none for is left out
 */
@AggregateRoot
public record Act(
    @Identity ActIdentifier id,
    @Nullable Eli eli,
    ResourceType type,
    @Nullable LocalDate dateOfDocument,
    boolean inForce,
    @Nullable LocalDate endOfValidity,
    Timeline timeline,
    List<ActDate> dates,
    Map<ActIdentifier, List<ActDate>> amendingDates) {

  public Act {
    dates = List.copyOf(dates);
    amendingDates =
        amendingDates.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /** The own dates of this act or of an act its versions integrate; none for another act. */
  public List<ActDate> datesOf(ActIdentifier act) {
    return act.equals(id) ? dates : amendingDates.getOrDefault(act, List.of());
  }

  /** The version that applies on {@code date}; none before the act's text first applies. */
  public Optional<Version> versionOn(LocalDate date) {
    return timeline.applicableOn(date);
  }
}
