// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The version date of a change: the first day of the first version whose text newly tags the
 * changed place with the amending act. {@link Divergences} compares it with the place date.
 *
 * @param version the version whose first day is the version date, or null if no version includes
 *     the amending act
 * @param tagging every version whose text tags the place
 */
record VersionDate(@Nullable Version version, Set<Version> tagging) {

  VersionDate {
    tagging = Set.copyOf(tagging);
  }

  /**
   * Returns the version date of the change {@code place} of {@code amending}. An occurrence tags
   * the place if it is an occurrence of the place, or an occurrence in it. If no version tags it,
   * it is the first version that tags an occurrence around the place: 2022/1190 deletes Article
   * 48(9) of 2018/1862, and the text of 1 August 2022 keeps the paragraph and tags Article 48. It
   * is the version that starts on the place date instead, if that version also newly tags the
   * place.
   *
   * <p>Returns a version date without a version if no version includes {@code amending}. Returns
   * empty if a version before the answer has no built text, or if no version tags the place while
   * some version includes the act.
   *
   * @param versions the versions of the act in the language, in order
   * @param texts the tags of the texts that are built, by version
   * @param occurrences the occurrences of the text of a version, made ready one time
   * @param includes whether a version includes {@code amending}
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-version-date-of-a-change")
  static Optional<VersionDate> of(
      ActIdentifier amending,
      Amendment place,
      List<Version> versions,
      Map<Version, TextTags> texts,
      Function<Version, TextOccurrences> occurrences,
      Predicate<Version> includes) {
    Version first = null;
    Version around = null;
    var newly = new ArrayList<Version>();
    var tagging = new HashSet<Version>();
    Set<String> before = Set.of();
    for (var version : versions) {
      var text = texts.get(version);
      if (text == null) {
        if (first == null) {
          return Optional.empty();
        }
        continue;
      }
      var found =
          place
              .location()
              .in(occurrences.apply(version))
              .map(Location.Found::eIds)
              .orElseGet(List::of);
      if (found.isEmpty()) {
        continue;
      }
      var of = Set.copyOf(found);
      var holders = holders(found);
      var now = new HashSet<String>();
      for (var tag : text.tagged(amending)) {
        if (inOneOf(tag, of)) {
          now.add(tag);
        } else if (around == null && holders.contains(tag)) {
          around = version;
        }
      }
      if (!now.isEmpty()) {
        tagging.add(version);
      }
      if (!before.containsAll(now)) {
        newly.add(version);
        if (first == null) {
          first = version;
        }
      }
      before = now;
    }
    var from = place.from();
    var onTheDay = newly.stream().filter(each -> each.date().equals(from)).findFirst();
    if (onTheDay.isPresent()) {
      return Optional.of(new VersionDate(onTheDay.get(), tagging));
    }
    if (first != null || around != null) {
      return Optional.of(new VersionDate(first != null ? first : around, tagging));
    }
    return versions.stream().anyMatch(includes)
        ? Optional.empty()
        : Optional.of(new VersionDate(null, tagging));
  }

  /** Returns whether {@code eId} is one of {@code occurrences}, or an occurrence in one of them. */
  private static boolean inOneOf(String eId, Set<String> occurrences) {
    if (occurrences.contains(eId)) {
      return true;
    }
    for (var slash = eId.lastIndexOf('/'); slash > 0; slash = eId.lastIndexOf('/', slash - 1)) {
      if (occurrences.contains(eId.substring(0, slash))) {
        return true;
      }
    }
    return false;
  }

  /** Returns the eIds of the occurrences that hold one of {@code occurrences}. */
  private static Set<String> holders(List<String> occurrences) {
    var holders = new HashSet<String>();
    for (var occurrence : occurrences) {
      for (var slash = occurrence.indexOf('/');
          slash > 0;
          slash = occurrence.indexOf('/', slash + 1)) {
        holders.add(occurrence.substring(0, slash));
      }
    }
    return holders;
  }
}
