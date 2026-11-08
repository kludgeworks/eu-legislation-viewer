// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
  private final List<Version> versions;
  private final NavigableMap<LocalDate, List<Version>> byDate;

  /**
   * Makes a timeline of the versions, in any order. There is at most one version a day in any one
   * language. An act's original text and a consolidation may share a day when they apply to
   * different languages.
   */
  public Timeline(List<Version> versions) {
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("A timeline has at least the original text");
    }
    var sorted = new ArrayList<>(versions);
    sorted.sort(
        Comparator.comparing(Version::date)
            .thenComparingInt(v -> v.kind() == Version.Kind.ORIGINAL ? 0 : 1));

    for (int i = 0; i < sorted.size() - 1; i++) {
      var current = sorted.get(i);
      for (int j = i + 1; j < sorted.size(); j++) {
        var next = sorted.get(j);
        if (current.date().equals(next.date())) {
          if (current.kind() == next.kind()
              || (!current.languages().isEmpty()
                  && !next.languages().isEmpty()
                  && !Collections.disjoint(current.languages(), next.languages()))) {
            throw new IllegalArgumentException("Two versions on one day: " + current + ", " + next);
          }
        } else {
          break;
        }
      }
    }

    NavigableMap<LocalDate, List<Version>> byDateMap = new TreeMap<>();
    for (var v : sorted) {
      byDateMap.computeIfAbsent(v.date(), d -> new ArrayList<>()).add(v);
    }
    this.versions = List.copyOf(sorted);
    this.byDate = Collections.unmodifiableNavigableMap(byDateMap);
  }

  /** Returns the versions in date order. */
  public List<Version> versions() {
    return versions;
  }

  /**
   * Returns the version that applies on {@code date}, or empty before the first applies.
   *
   * @throws IllegalStateException if this timeline mixes versions of different languages on a
   *     shared day; use {@link #applicableOn(LocalDate, Language)} or {@link #in(Language)}
   */
  public Optional<Version> applicableOn(LocalDate date) {
    checkSingleVersionPerDay();
    var entry = byDate.floorEntry(date);
    return entry == null ? Optional.empty() : Optional.of(entry.getValue().getFirst());
  }

  /**
   * Returns the version that applies on {@code date} for a reader in {@code language}, or empty
   * before the first version applies. When multiple versions share a day (e.g. an original text and
   * a same-day consolidation in different languages), it picks the one in {@code language}, falling
   * back to the other.
   */
  public Optional<Version> applicableOn(LocalDate date, Language language) {
    var entry = byDate.floorEntry(date);
    if (entry == null) {
      return Optional.empty();
    }
    var onDate = entry.getValue();
    if (onDate.size() == 1) {
      return Optional.of(onDate.getFirst());
    }
    return onDate.stream()
        .filter(v -> v.languages().contains(language))
        .findFirst()
        .or(() -> Optional.of(onDate.getFirst()));
  }

  /**
   * Returns the version that starts on {@code date}, or empty if no version does. {@link
   * #applicableOn} returns the version that applies.
   *
   * @throws IllegalStateException if this timeline mixes versions of different languages on a
   *     shared day; use {@link #startingOn(LocalDate, Language)} or {@link #in(Language)}
   */
  public Optional<Version> startingOn(LocalDate date) {
    checkSingleVersionPerDay();
    var onDate = byDate.get(date);
    return onDate == null || onDate.isEmpty() ? Optional.empty() : Optional.of(onDate.getFirst());
  }

  /**
   * Returns the version that starts on {@code date} in {@code language}, or empty if no version
   * does. When multiple versions share the start day, it picks the one in {@code language}, falling
   * back to the other.
   */
  public Optional<Version> startingOn(LocalDate date, Language language) {
    var onDate = byDate.get(date);
    if (onDate == null || onDate.isEmpty()) {
      return Optional.empty();
    }
    if (onDate.size() == 1) {
      return Optional.of(onDate.getFirst());
    }
    return onDate.stream()
        .filter(v -> v.languages().contains(language))
        .findFirst()
        .or(() -> Optional.of(onDate.getFirst()));
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
    var inLanguage = versions.stream().filter(v -> v.languages().contains(language)).toList();
    return inLanguage.isEmpty() ? Optional.empty() : Optional.of(new Timeline(inLanguage));
  }

  /** Returns the first version, from which the act's text first applies. */
  public Version first() {
    return versions.getFirst();
  }

  /** Returns the latest version, which may apply only in the future. */
  public Version latest() {
    return versions.getLast();
  }

  /**
   * Returns the version before {@code version}, or empty for the first.
   *
   * @throws IllegalStateException if this timeline mixes versions of different languages on a
   *     shared day; use {@link #in(Language)}
   */
  public Optional<Version> predecessor(Version version) {
    checkSingleVersionPerDay();
    int index = indexOf(version);
    return index > 0 ? Optional.of(versions.get(index - 1)) : Optional.empty();
  }

  /**
   * Returns the version after {@code version}, or empty for the latest.
   *
   * @throws IllegalStateException if this timeline mixes versions of different languages on a
   *     shared day; use {@link #in(Language)}
   */
  public Optional<Version> successor(Version version) {
    checkSingleVersionPerDay();
    int index = indexOf(version);
    return index < versions.size() - 1 ? Optional.of(versions.get(index + 1)) : Optional.empty();
  }

  private void checkSingleVersionPerDay() {
    if (versions.size() != byDate.size()) {
      throw new IllegalStateException(
          "Timeline has multiple versions on one day in different languages; use in(language)");
    }
  }

  private int indexOf(Version version) {
    int index = versions.indexOf(version);
    if (index < 0) {
      throw new IllegalArgumentException("Not a version of this act: " + version);
    }
    return index;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Timeline timeline && Objects.equals(versions, timeline.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(versions);
  }

  @Override
  public String toString() {
    return "Timeline" + versions;
  }
}
