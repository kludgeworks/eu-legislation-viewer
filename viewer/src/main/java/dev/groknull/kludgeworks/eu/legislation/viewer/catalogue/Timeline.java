// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An act's versions, by the day from which each applies until the next one applies.
 *
 * <p>The version that applies on a date is the last one that starts on or before that date. Interim
 * versions are included once their date has passed. After a repeal, the last version applies.
 */
@ValueObject
public final class Timeline {
  private final NavigableMap<LocalDate, Version> byDate;

  /** Makes a timeline of the versions, in any order. There is at most one version a day. */
  public Timeline(List<Version> versions) {
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("A timeline has at least the original text");
    }
    NavigableMap<LocalDate, Version> byDate =
        versions.stream()
            .collect(
                Collectors.toMap(
                    Version::date,
                    Function.identity(),
                    (one, other) -> {
                      throw new IllegalArgumentException(
                          "Two versions on one day: " + one + ", " + other);
                    },
                    TreeMap::new));
    this.byDate = Collections.unmodifiableNavigableMap(byDate);
  }

  /** Returns the versions in date order. */
  public List<Version> versions() {
    return List.copyOf(byDate.values());
  }

  /** Returns the version that applies on {@code date}, or empty before the first applies. */
  public Optional<Version> applicableOn(LocalDate date) {
    return value(byDate.floorEntry(date));
  }

  /**
   * Returns the version that starts on {@code date}, or empty if no version does. {@link
   * #applicableOn} returns the version that applies.
   */
  public Optional<Version> startingOn(LocalDate date) {
    return Optional.ofNullable(byDate.get(date));
  }

  /**
   * Returns the versions in {@code language}, as a timeline of their own. {@link Version#languages}
   * tells which languages a version is in.
   *
   * <p>A reader of that language steps through this timeline: its first version, its latest
   * version, and the versions either side of a version. A version without the language is passed
   * over. Returns empty if no version is in the language.
   */
  public Optional<Timeline> in(Language language) {
    var versions = byDate.values().stream().filter(v -> v.languages().contains(language)).toList();
    return versions.isEmpty() ? Optional.empty() : Optional.of(new Timeline(versions));
  }

  /** Returns the first version, from which the act's text first applies. */
  public Version first() {
    return byDate.firstEntry().getValue();
  }

  /** Returns the latest version, which may apply only in the future. */
  public Version latest() {
    return byDate.lastEntry().getValue();
  }

  /** Returns the version before {@code version}, or empty for the first. */
  public Optional<Version> predecessor(Version version) {
    return value(byDate.lowerEntry(dateOf(version)));
  }

  /** Returns the version after {@code version}, or empty for the latest. */
  public Optional<Version> successor(Version version) {
    return value(byDate.higherEntry(dateOf(version)));
  }

  private LocalDate dateOf(Version version) {
    if (!version.equals(byDate.get(version.date()))) {
      throw new IllegalArgumentException("Not a version of this act: " + version);
    }
    return version.date();
  }

  private static Optional<Version> value(Map.@Nullable Entry<LocalDate, Version> entry) {
    return Optional.ofNullable(entry).map(Map.Entry::getValue);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Timeline timeline && Objects.equals(byDate, timeline.byDate);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(byDate);
  }

  @Override
  public String toString() {
    return "Timeline" + byDate.values();
  }
}
