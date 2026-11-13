// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.AmendmentsChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LanguageAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionRepublished;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.VersionWithdrawn;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * What a read of a held act from Cellar changes: the differences between the act as stored and as
 * Cellar has it now, each as one event.
 *
 * <ul>
 *   <li>{@link VersionAdded}: a version that the store does not have.
 *   <li>{@link LanguageAdded}: a stored version in a language that the store does not have it in.
 *   <li>{@link VersionRepublished}: a stored version whose Formex in a language Cellar republished
 *       ({@link Version.Formex#republishedAs}). A language whose Formex the store does not have
 *       yet, for example one stored before the store kept it, is not republished: its Formex is
 *       stored as Cellar has it.
 *   <li>{@link VersionWithdrawn}: a stored version that two reads at least {@code withdrawnAfter}
 *       apart do not have. Cellar deletes a work and ingests it again with the same identity, so
 *       one read is not enough. The first read that misses it notes the time in {@code missing},
 *       and asks for a second read at {@link #recheckAt}. A version that comes back is no longer
 *       missing.
 *   <li>{@link AmendmentsChanged}: the act's amendments, or the dates of the acts that amend it,
 *       changed.
 * </ul>
 *
 * @param events the events, in the order above
 * @param missing the stored versions that Cellar does not have and that are not withdrawn yet, by
 *     CELEX, with when a read first did not have each
 * @param withdrawn the CELEX of each version withdrawn now
 * @param recheckAt when to read the act again, for the versions in {@code missing}. Null if none is
 *     missing.
 */
record Refresh(
    List<Object> events,
    Map<String, Instant> missing,
    Set<String> withdrawn,
    @Nullable Instant recheckAt) {

  Refresh {
    events = List.copyOf(events);
    missing = Map.copyOf(missing);
    withdrawn = Set.copyOf(withdrawn);
  }

  /**
   * Compares the act as stored with the act as Cellar has it now.
   *
   * @param stored the act as stored
   * @param missing the stored versions that earlier reads did not have, by CELEX, with when the
   *     first such read was
   * @param read the act as Cellar has it now
   * @param now when Cellar was read
   * @param withdrawnAfter how long apart two reads that miss a version are, at least, before it is
   *     withdrawn
   */
  static Refresh of(
      Act stored, Map<String, Instant> missing, Act read, Instant now, Duration withdrawnAfter) {
    var before = byCelex(stored);
    var after = byCelex(read);
    var events = new ArrayList<Object>();
    for (var version : after.values()) {
      var kept = before.get(version.celex());
      if (kept == null) {
        events.add(
            new VersionAdded(read.id(), version.celex(), version.date(), version.languages()));
        continue;
      }
      for (var language : new TreeSet<>(version.languages())) {
        var formex = version.formex().get(language);
        var keptFormex = kept.formex().get(language);
        if (!kept.languages().contains(language)) {
          events.add(new LanguageAdded(read.id(), version.celex(), language));
        } else if (formex != null && keptFormex != null && keptFormex.republishedAs(formex)) {
          events.add(
              new VersionRepublished(read.id(), version.celex(), language, formex.modified()));
        }
      }
    }
    var stillMissing = new TreeMap<String, Instant>();
    var withdrawn = new HashSet<String>();
    Instant recheckAt = null;
    for (var celex : before.keySet()) {
      if (after.containsKey(celex)) {
        continue;
      }
      var since = missing.getOrDefault(celex, now);
      if (Duration.between(since, now).compareTo(withdrawnAfter) >= 0) {
        withdrawn.add(celex);
        events.add(new VersionWithdrawn(celex));
      } else {
        stillMissing.put(celex, since);
        var at = since.plus(withdrawnAfter);
        recheckAt = recheckAt == null || at.isBefore(recheckAt) ? at : recheckAt;
      }
    }
    if (!Set.copyOf(stored.amendments()).equals(Set.copyOf(read.amendments()))
        || !stored.amendingDates().equals(read.amendingDates())) {
      events.add(new AmendmentsChanged(read.id()));
    }
    return new Refresh(events, stillMissing, withdrawn, recheckAt);
  }

  private static Map<String, Version> byCelex(Act act) {
    var versions = new LinkedHashMap<String, Version>();
    act.timeline().versions().forEach(version -> versions.put(version.celex(), version));
    return versions;
  }
}
