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
 *
 * <p>Today is the day in Brussels, {@link TheVersionsOfAnAct#today}: the EU adopts its acts there,
 * and the Official Journal dates them there.
 */
@ValueObject
public final class Timeline {

  private final List<Version> versions;
  private final NavigableMap<LocalDate, List<Version>> byDate;

  /**
   * Makes a timeline of the versions, in any order. There is at most one version a day in any one
   * language. An act's original text and a consolidation may share a day when they apply to
   * different languages. It refuses versions that do not obey this.
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
    return allApplicableOn(date).stream().findFirst();
  }

  /**
   * Returns the version that applies on {@code date} for a reader in {@code language}, or empty
   * before the first version applies. It is the version in {@code language} of the versions of the
   * day ({@link WhatAComparisonCovers#oneVersionADayInEachLanguage}). The returned version applies
   * on {@code date} across the act, but may lack {@code language}.
   */
  public Optional<Version> applicableOn(LocalDate date, Language language) {
    return Optional.of(allApplicableOn(date))
        .filter(ofTheDay -> !ofTheDay.isEmpty())
        .map(ofTheDay -> WhatAComparisonCovers.oneVersionADayInEachLanguage(ofTheDay, language));
  }

  /**
   * Returns the versions that apply on {@code date}, in any language. That is one version, or the
   * original text and a consolidation that share the day, in different languages. Returns none
   * before the first version applies.
   */
  public List<Version> allApplicableOn(LocalDate date) {
    return TheTextAndTheLawOnADay.theTextOfADayIsAVersion(byDate, date);
  }

  /**
   * Returns the day on which the next version after {@code date} starts. The versions that apply on
   * {@code date} apply until that day. Returns empty if no version starts later.
   */
  public Optional<LocalDate> nextStartAfter(LocalDate date) {
    return Optional.ofNullable(byDate.higherKey(date));
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
   * does. It is the version in {@code language} of the versions that start on the day ({@link
   * WhatAComparisonCovers#oneVersionADayInEachLanguage}). The returned version starts on {@code
   * date} across the act, but may lack {@code language}.
   */
  public Optional<Version> startingOn(LocalDate date, Language language) {
    return Optional.ofNullable(byDate.get(date))
        .filter(ofTheDay -> !ofTheDay.isEmpty())
        .map(ofTheDay -> WhatAComparisonCovers.oneVersionADayInEachLanguage(ofTheDay, language));
  }

  /**
   * Returns the versions in {@code language}, as a timeline of their own. {@link Version#languages}
   * tells which languages a version is in.
   *
   * <p>A reader of that language moves through this timeline: its first version, its latest
   * version, and the versions either side of a version. A version without the language is passed
   * over. Returns empty if no version is in the language.
   */
  public Optional<Timeline> in(Language language) {
    var inLanguage = versions.stream().filter(v -> v.languages().contains(language)).toList();
    return inLanguage.isEmpty() ? Optional.empty() : Optional.of(new Timeline(inLanguage));
  }

  /**
   * Returns whether {@code date} is before the first version: no version applies on it. For
   * example, a day before the act entered into force.
   */
  public boolean startsAfter(LocalDate date) {
    return date.isBefore(first().date());
  }

  /**
   * Returns the latest version that starts before {@code date}, or empty if none does. {@code date}
   * need not be the first day of a version.
   */
  public Optional<Version> lastBefore(LocalDate date) {
    return Optional.ofNullable(byDate.lowerEntry(date)).map(entry -> entry.getValue().getFirst());
  }

  /**
   * Returns the first version that starts after {@code date}, or empty if none does. {@code date}
   * need not be the first day of a version.
   */
  public Optional<Version> firstAfter(LocalDate date) {
    return Optional.ofNullable(byDate.higherEntry(date)).map(entry -> entry.getValue().getFirst());
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

  /**
   * The two days of a comparison: From, the earlier day, and To, the later day. A day need not be
   * the first day of a version: the address of a comparison keeps the days that the reader asked
   * for.
   *
   * @param from the earlier day
   * @param to the later day, or the same day
   */
  public record TwoDays(LocalDate from, LocalDate to) {
    public TwoDays {
      if (from.isAfter(to)) {
        throw new IllegalArgumentException("From is after To: " + from + ", " + to);
      }
    }
  }

  /**
   * The two versions that a comparison is between.
   *
   * @param from the version of the earlier day
   * @param to the version of the later day, or the same version
   */
  public record TwoVersions(Version from, Version to) {}

  /**
   * Returns the two days of a comparison of the versions of this timeline, in one language, from
   * the days that the reader gave ({@link WhatAComparisonCovers#theTwoDaysOfAComparison}). {@code
   * today} is the day on which the reader asks ({@link TheVersionsOfAnAct#today}).
   */
  public TwoDays twoDays(@Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
    return WhatAComparisonCovers.theTwoDaysOfAComparison(this, from, to, today);
  }

  /**
   * Returns the day on which a comparison from the first version can end, where the reader asked
   * for a From before the first version: {@code to}, or the first day of the second version if
   * {@code to} is before the first version also.
   */
  public LocalDate endFromTheFirst(LocalDate to) {
    var first = first();
    return startsAfter(to) ? successor(first).map(Version::date).orElseGet(first::date) : to;
  }

  /**
   * Returns the versions that start on {@code from} and on {@code to}: the two versions of a
   * comparison at its canonical address ({@link WhatAComparisonCovers#aComparisonIsOfTwoVersions}).
   * Returns empty if a day is not the first day of a version, or if {@code from} is after {@code
   * to}.
   */
  public Optional<TwoVersions> versionsStartingOn(LocalDate from, LocalDate to) {
    return WhatAComparisonCovers.aComparisonIsOfTwoVersions(
        from, to, startingOn(from).orElse(null), startingOn(to).orElse(null));
  }

  /**
   * Returns the versions of a comparison of the law on {@code from} with the law on {@code to}, in
   * {@code language}: the versions that apply on the two days ({@link
   * WhatAComparisonCovers#twoDaysAtTheirVersions}). Returns empty if no version applies on a day.
   */
  public Optional<TwoVersions> versionsOn(LocalDate from, LocalDate to, Language language) {
    return WhatAComparisonCovers.twoDaysAtTheirVersions(
        applicableOn(from, language).orElse(null), applicableOn(to, language).orElse(null));
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
