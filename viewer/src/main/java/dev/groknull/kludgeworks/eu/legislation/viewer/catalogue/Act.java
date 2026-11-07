// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An act. It is a work in Cellar, identified by its CELEX and its ELI, and it has a timeline.
 *
 * @param id the act's CELEX
 * @param eli its ELI. Older acts may have none.
 * @param type its resource type, for example REG_IMPL, DIR or DEC
 * @param titles its title in each official language that Cellar has it in, as the Official Journal
 *     printed it
 * @param dateOfDocument the date it was adopted or signed
 * @param inForce whether it is in force, as Cellar says
 * @param endOfValidity the day it ceases to apply, or null while it is open-ended
 * @param timeline its versions
 * @param dates its own dates. These are the dates when it enters into force, applies or takes
 *     effect.
 * @param amendingDates the own dates of each act that its versions integrate, by act. An act that
 *     Cellar has no dates for is left out.
 * @param amendments what other acts did to its provisions, as Cellar annotates them. Each amendment
 *     has one location.
 * @param publications how this act and each act that its versions integrate were published. Each
 *     publication has the ELI, the titles and the Official Journal reference. An act that Cellar
 *     has nothing for is left out.
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
    Map<ActIdentifier, List<ActDate>> amendingDates,
    List<Amendment> amendments,
    Map<ActIdentifier, Publication> publications) {

  public Act {
    publications = Map.copyOf(publications);
    titles = Map.copyOf(titles);
    dates = List.copyOf(dates);
    amendingDates =
        amendingDates.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    amendments = List.copyOf(amendments);
  }

  /** Returns its title in {@code language}, or empty if Cellar has no title in that language. */
  public Optional<String> title(Language language) {
    return Optional.ofNullable(titles.get(language));
  }

  /**
   * Returns the own dates of {@code act}, if it is this act or an act that its versions integrate.
   * For any other act, returns an empty list.
   */
  public List<ActDate> datesOf(ActIdentifier act) {
    return act.equals(id) ? dates : amendingDates.getOrDefault(act, List.of());
  }

  /**
   * Returns how {@code act} was published. The act is this act or an act that its versions
   * integrate. Returns empty if Cellar does not say.
   */
  public Optional<Publication> publication(ActIdentifier act) {
    return Optional.ofNullable(publications.get(act));
  }

  /**
   * Returns the acts that the versions after {@code from}, up to and including {@code to},
   * integrate.
   */
  public Set<ActIdentifier> integratedBetween(Version from, Version to) {
    var between = new HashSet<ActIdentifier>();
    for (var version : timeline.versions()) {
      if (version.date().isAfter(from.date()) && !version.date().isAfter(to.date())) {
        between.addAll(version.integrates());
      }
    }
    return between;
  }

  /**
   * Returns Cellar's amendments of this act's provisions, placed at the units of two texts.
   *
   * <p>An amendment is included if its act is integrated between {@code from} and {@code to}, and
   * if it changes the text in {@code language}. An amendment that applies only after {@code to} is
   * left out. An amendment that applies before {@code from} is kept. A version can lag the law by
   * weeks, so its date does not tell what the version already contains.
   *
   * <ul>
   *   <li>An addition or a replacement is placed at a unit of the later text. {@code later} holds
   *       the eIds of its units, in document order.
   *   <li>A deletion or a repeal is placed at a unit of the earlier text. {@code earlier} holds the
   *       eIds of its units, in document order.
   *   <li>An amendment whose location is in neither text is counted as unplaced.
   * </ul>
   */
  public Amended amended(
      Version from, Version to, List<String> earlier, List<String> later, Language language) {
    var between = integratedBetween(from, to);
    var byUnit = new LinkedHashMap<String, List<Amendment>>();
    var unplaced = 0;
    for (var amendment : amendments) {
      var applies = amendment.from();
      // An amendment that applies only after the later version is not in its text. It is a later
      // stage of the act.
      if (!between.contains(amendment.act())
          || !amendment.changes(language)
          || (applies != null && applies.isAfter(to.date()))) {
        continue;
      }
      var found = amendment.location().in(amendment.role().removes() ? earlier : later);
      if (found.isEmpty()) {
        unplaced++;
        continue;
      }
      for (var eId : found.get().eIds()) {
        byUnit.computeIfAbsent(eId, unit -> new ArrayList<>()).add(amendment);
      }
    }
    return new Amended(byUnit, unplaced);
  }

  /**
   * Returns the version that applies on {@code date}. Returns empty before the text of the act
   * first applies.
   */
  public Optional<Version> versionOn(LocalDate date) {
    return timeline.applicableOn(date);
  }

  /**
   * Returns the acts that already apply while {@code version} applies, but that only a later
   * version includes. Thus the text of {@code version} is not yet the law.
   *
   * <p>For example, 2017/815 applies from 1 June 2017. On that day the version of 2015/1998 of 29
   * February 2016 applies. The version of 7 June is the first to include 2017/815.
   *
   * <p>An act applies from its earliest date of application or of taking effect, in whole or in
   * part. Dates in part count, because if all the dates are in part, the parts make the whole act.
   * An act that has neither kind of date applies from its entry into force. An act whose date
   * Cellar does not know is left out. The acts are in the order in which the later versions include
   * them.
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
   * Returns the acts that apply before the first version that includes them. Each act lags the law
   * from the day it applies, as {@link #notYetConsolidated} finds it, to the date of that version.
   * The acts are in the order in which the versions include them.
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

  /**
   * Returns the acts in force while {@code version} applies, whose date of application Cellar does
   * not know, and that only a later version includes. Cellar does not know the date if it has only
   * a placeholder, or no date with a day. The text of {@code version} may lag the law from the day
   * each of these acts entered into force.
   *
   * <p>For example, 2019/413 is in force from 16 March 2019. It applies from a date that its
   * Article 3 sets. The version of 15 March 2021 is the first to include it.
   *
   * <p>The acts are in the order in which the later versions include them.
   */
  public List<Uncertain> mayLag(Version version) {
    var until = timeline.successor(version).map(Version::date);
    if (until.isEmpty()) {
      return List.of();
    }
    var included = new HashSet<ActIdentifier>();
    var uncertain = new ArrayList<Uncertain>();
    for (var each : timeline.versions()) {
      for (var act : each.integrates()) {
        if (!included.add(act) || !each.date().isAfter(version.date())) {
          continue;
        }
        var dates = datesOf(act);
        var applying = dates.stream().filter(date -> date.kind() != Kind.ENTRY_INTO_FORCE).toList();
        if (applying.isEmpty() || applying.stream().anyMatch(date -> date.date() != null)) {
          continue;
        }
        dates.stream()
            .filter(date -> date.kind() == Kind.ENTRY_INTO_FORCE && date.date() != null)
            .map(date -> Objects.requireNonNull(date.date()))
            .min(Comparator.naturalOrder())
            .filter(inForce -> inForce.isBefore(until.get()))
            .ifPresent(
                inForce ->
                    uncertain.add(new Uncertain(act, inForce, applying.getFirst().clause(), each)));
      }
    }
    return uncertain;
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
   * An act that is in force before a version of this act includes it, and whose date of application
   * Cellar does not know. The text may lag the law.
   *
   * @param act the act
   * @param inForce the day it entered into force
   * @param clause what sets the date it applies from, for example Article 3
   * @param includedIn the first version that includes it
   */
  @ValueObject
  public record Uncertain(
      ActIdentifier act, LocalDate inForce, ActDate.Clause clause, Version includedIn) {}

  /**
   * An act that applies before a version of this one includes it.
   *
   * @param act the act
   * @param appliesFrom the date it applies from, with what sets it
   * @param includedIn the first version that includes it
   */
  @ValueObject
  public record Unconsolidated(ActIdentifier act, ActDate appliesFrom, Version includedIn) {

    /**
     * Returns the day it applies from. This is the date of {@link #appliesFrom}, which an act that
     * a version lags always has.
     */
    public LocalDate from() {
      return Objects.requireNonNull(appliesFrom.date());
    }
  }
}
