// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An act: a work in Cellar, identified by its CELEX and its ELI, with its timeline.
 *
 * @param id the act's CELEX
 * @param eli its ELI; older acts may have none
 * @param type its resource type (REG_IMPL, DIR, DEC)
 * @param titles its title in each official language Cellar has it in, as the Official Journal
 *     printed it
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
    Map<Language, String> titles,
    @Nullable LocalDate dateOfDocument,
    boolean inForce,
    @Nullable LocalDate endOfValidity,
    Timeline timeline,
    List<ActDate> dates,
    Map<ActIdentifier, List<ActDate>> amendingDates) {

  public Act {
    titles = Map.copyOf(titles);
    dates = List.copyOf(dates);
    amendingDates =
        amendingDates.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /** Its title in {@code language}; none if Cellar has none in it. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
  }

  /** The own dates of this act or of an act its versions integrate; none for another act. */
  public List<ActDate> datesOf(ActIdentifier act) {
    return act.equals(id) ? dates : amendingDates.getOrDefault(act, List.of());
  }

  /** The version that applies on {@code date}; none before the act's text first applies. */
  public Optional<Version> versionOn(LocalDate date) {
    return timeline.applicableOn(date);
  }

  /**
   * The acts that already apply while {@code version} does, but that only a later version includes,
   * so that its text isn't yet the law: 2017/815 applies from 1 June 2017, while 2015/1998's
   * version of 29 February 2016 does, and is first included in the version of 7 June. An act
   * applies from its earliest date of application or taking effect, in whole or in part (when all
   * are in part, the parts are the whole act), or from its entry into force if it has neither; one
   * whose date Cellar doesn't know is left out. In the order the later versions include them.
   */
  public List<Unconsolidated> notYetConsolidated(Version version) {
    var until = timeline.successor(version).map(Version::date);
    if (until.isEmpty()) {
      return List.of();
    }
    return lags().stream()
        .filter(lag -> lag.includedIn().date().isAfter(version.date()))
        .filter(lag -> lag.from().isBefore(until.get()))
        .toList();
  }

  /**
   * The acts that apply before the first version that includes them: each lags the law from the day
   * it applies (as notYetConsolidated has it) to that version's. In the order the versions include
   * them.
   */
  public List<Unconsolidated> lags() {
    var included = new HashSet<ActIdentifier>();
    var lags = new ArrayList<Unconsolidated>();
    for (var each : timeline.versions()) {
      for (var act : each.integrates()) {
        if (included.add(act)) {
          appliesFrom(act)
              .filter(from -> Objects.requireNonNull(from.date()).isBefore(each.date()))
              .ifPresent(from -> lags.add(new Unconsolidated(act, from, each)));
        }
      }
    }
    return lags;
  }

  private Optional<ActDate> appliesFrom(ActIdentifier act) {
    var dates = datesOf(act);
    var applying = dates.stream().filter(date -> date.kind() != Kind.ENTRY_INTO_FORCE).toList();
    return (applying.isEmpty() ? dates : applying)
        .stream()
            .filter(date -> date.date() != null)
            .min(Comparator.comparing(date -> Objects.requireNonNull(date.date())));
  }

  /**
   * An act that applies before a version of this one includes it.
   *
   * @param act the act
   * @param appliesFrom the date it applies from, with what sets it
   * @param includedIn the first version that includes it
   */
  @ValueObject
  public record Unconsolidated(ActIdentifier act, ActDate appliesFrom, Version includedIn) {

    /** The day it applies from: appliesFrom's date, which an act it lags by always has. */
    public LocalDate from() {
      return Objects.requireNonNull(appliesFrom.date());
    }
  }
}
