// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * <p>Today is the day in Brussels, {@link #today}: the EU adopts its acts there, and the Official
 * Journal dates them there.
 */
@ValueObject
public final class Timeline {
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  private final List<Version> versions;
  private final NavigableMap<LocalDate, List<Version>> byDate;

  /** Returns today in the EU: the date in Brussels. */
  @BusinessRule(group = RuleGroup.THE_VERSIONS_OF_AN_ACT, name = "today-is-the-day-in-brussels")
  public static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), BRUSSELS);
  }

  /**
   * Makes a timeline of the versions, in any order. There is at most one version a day in any one
   * language. An act's original text and a consolidation may share a day when they apply to
   * different languages.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "one-version-a-day-in-each-language")
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
   * before the first version applies. The returned version applies on {@code date} across the act,
   * but may lack {@code language}. When multiple versions share a day (e.g. an original text and a
   * same-day consolidation in different languages), it picks the one in {@code language}, falling
   * back to the other.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "one-version-a-day-in-each-language")
  @BusinessRule(group = RuleGroup.THE_VERSIONS_OF_AN_ACT, name = "the-last-version-after-a-repeal")
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
   * Returns the versions that apply on {@code date}, in any language. That is one version, or the
   * original text and a consolidation that share the day, in different languages. Returns none
   * before the first version applies.
   */
  public List<Version> allApplicableOn(LocalDate date) {
    var entry = byDate.floorEntry(date);
    return entry == null ? List.of() : List.copyOf(entry.getValue());
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
   * does. The returned version starts on {@code date} across the act, but may lack {@code
   * language}. When multiple versions share the start day, it picks the one in {@code language},
   * falling back to the other.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "one-version-a-day-in-each-language")
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
   * the days that the reader gave. {@code today} is the day on which the reader asks ({@link
   * #today}).
   *
   * <ul>
   *   <li>Without To, To is the first day of the version that applies on {@code today}, or of the
   *       latest version if none applies on {@code today}.
   *   <li>Without From, From is the first day of the version before the version of To. If the
   *       version of To is the first version, From is its first day, and To is the first day of the
   *       version after it.
   *   <li>A day that the reader gave stays as given. If From is after To, the two days change
   *       places.
   * </ul>
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "the-days-when-the-reader-gives-none")
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "the-days-as-the-reader-gives-them")
  public TwoDays twoDays(@Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
    var later = to != null ? to : applicableOn(today).orElseGet(this::latest).date();
    var earlier = from;
    if (earlier == null) {
      var version = applicableOn(later);
      var predecessor = version.flatMap(this::predecessor);
      if (version.isEmpty() || predecessor.isPresent()) {
        earlier = predecessor.map(Version::date).orElse(later);
      } else {
        earlier = later;
        later = successor(version.get()).map(Version::date).orElse(later);
      }
    }
    return earlier.isAfter(later) ? new TwoDays(later, earlier) : new TwoDays(earlier, later);
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
   * comparison at its canonical address. Returns empty if a day is not the first day of a version,
   * or if {@code from} is after {@code to}.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "a-comparison-is-of-two-versions")
  public Optional<TwoVersions> versionsStartingOn(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      return Optional.empty();
    }
    return startingOn(from)
        .flatMap(earlier -> startingOn(to).map(later -> new TwoVersions(earlier, later)));
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
