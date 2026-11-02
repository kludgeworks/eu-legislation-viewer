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
 * An act's versions by the day each applies from, until the next one's. Which applies on a date:
 * the last that starts on or before it (Design: Choosing the version to show), interim ones
 * included once their date has passed; after a repeal, the last.
 */
@ValueObject
public final class Timeline {
  private final NavigableMap<LocalDate, Version> byDate;

  /** The versions, in any order; at most one a day. */
  public Timeline(List<Version> versions) {
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("A timeline has at least the original text");
    }
    TreeMap<LocalDate, Version> byDate =
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

  /** The versions in date order. */
  public List<Version> versions() {
    return List.copyOf(byDate.values());
  }

  /** The version that applies on {@code date}; none before the first applies. */
  public Optional<Version> applicableOn(LocalDate date) {
    return value(byDate.floorEntry(date));
  }

  /**
   * The version that starts on {@code date}; none if none does (applicableOn gives the one that
   * applies).
   */
  public Optional<Version> startingOn(LocalDate date) {
    return Optional.ofNullable(byDate.get(date));
  }

  /**
   * The versions in {@code language} (Version.languages), as a timeline of their own: its first,
   * latest and those either side of a version are the ones a reader of that language steps through,
   * a version without it passed over. None if no version is in it.
   */
  public Optional<Timeline> in(Language language) {
    var versions = byDate.values().stream().filter(v -> v.languages().contains(language)).toList();
    return versions.isEmpty() ? Optional.empty() : Optional.of(new Timeline(versions));
  }

  /** The first version, from which the act's text first applies. */
  public Version first() {
    return byDate.firstEntry().getValue();
  }

  /** The latest version, which may apply only in the future. */
  public Version latest() {
    return byDate.lastEntry().getValue();
  }

  /** The version before {@code version}, none for the first. */
  public Optional<Version> predecessor(Version version) {
    return value(byDate.lowerEntry(dateOf(version)));
  }

  /** The version after {@code version}, none for the latest. */
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
