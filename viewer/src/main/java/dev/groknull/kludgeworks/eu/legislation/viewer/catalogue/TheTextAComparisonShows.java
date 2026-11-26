// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The text a comparison shows": one function for each of its rules. A rule reads facts
 * and gives one new fact. It does not call another rule: {@link LawOverTime} and {@link
 * Prospective} apply the rules.
 */
public final class TheTextAComparisonShows {

  private TheTextAComparisonShows() {}

  /**
   * The comparison shows the text of the version that applies on the day. If an amending act
   * applies on the day and that version does not include it, the comparison shows the text of the
   * first version that includes the act. If there are two or more such acts, it is the latest of
   * their versions. An act that no version includes does not change the text.
   *
   * <p>For example, 2017/815 applies from 1 June 2017, and the version of 2015/1998 of 7 June 2017
   * is the first to include it. The text for 3 June 2017 is the version of 7 June 2017, not the
   * version of 29 February 2016 that applies on that day.
   *
   * @param applies the version in the language that applies on the day, or null before the first
   * @param ofTheDay the divergences of the day
   * @return the version whose text the comparison shows. Empty before the first version.
   */
  @BusinessRule(group = RuleGroup.THE_TEXT_A_COMPARISON_SHOWS, name = "the-text-for-a-day")
  public static Optional<Version> theTextForADay(
      @Nullable Version applies, List<Divergence> ofTheDay) {
    if (applies == null) {
      return Optional.empty();
    }
    return ofTheDay.stream()
        .filter(each -> each.kind() == Divergence.Kind.PENDING)
        .map(Divergence::included)
        .filter(Objects::nonNull)
        .filter(included -> included.date().isAfter(applies.date()))
        .max(Comparator.comparing(Version::date))
        .or(() -> Optional.of(applies));
  }

  /**
   * A text can have a change that does not apply on the day yet. That change comes from a
   * prospective amendment of the day. An occurrence has such a change if a prospective amendment of
   * the day changes one of these occurrences:
   *
   * <ul>
   *   <li>the occurrence itself
   *   <li>an occurrence that contains it
   *   <li>an occurrence in it
   * </ul>
   *
   * <p>The eIds tell which occurrence is in which. A change at another occurrence beside it does
   * not count. For example, the text of 2018/1862 of 1 August 2022 includes the changes of
   * 2022/1190, which apply from 15 October 2026. On 1 August 2022, Article 3, point 8 has such a
   * change, and so has Article 3. Article 3, point 1 has none.
   *
   * @param occurrence the eId of the occurrence
   * @param changed the act of each prospective amendment of the day, by the eId of the occurrence
   *     of the text that it changes
   * @return the act of the change. Empty if each change there applies.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_A_COMPARISON_SHOWS,
      name = "changes-that-do-not-apply-yet")
  public static Optional<ActIdentifier> changesThatDoNotApplyYet(
      String occurrence, Map<String, ActIdentifier> changed) {
    var itself = changed.get(occurrence);
    if (itself != null) {
      return Optional.of(itself);
    }
    return changed.entrySet().stream()
        .filter(
            each -> Amended.in(occurrence, each.getKey()) || Amended.in(each.getKey(), occurrence))
        .map(Map.Entry::getValue)
        .findFirst();
  }
}
