// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The occurrences of a text that have a prospective amendment on a day: a change that the text
 * includes, and that does not apply on the day yet. Its act applies there from a later day. {@link
 * LawOverTime#changesThatDoNotApplyYet} finds them.
 *
 * <p>For example, the text of 2018/1862 of 1 August 2022 includes the changes of 2022/1190, which
 * apply from 15 October 2026. On 1 August 2022, Article 3, point 8 is such an occurrence.
 *
 * @param day the day
 * @param byOccurrence the act of the change, by the eId of the occurrence
 */
@ValueObject
public record Prospective(LocalDate day, Map<String, ActIdentifier> byOccurrence) {

  public Prospective {
    byOccurrence = Map.copyOf(byOccurrence);
  }

  /**
   * Returns the act of a change at the occurrence {@code eId} that does not apply on the day yet.
   * The change is at the occurrence itself, at an occurrence that the occurrence is in, or at an
   * occurrence in it. Empty if each change there applies.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_A_COMPARISON_SHOWS,
      name = "changes-that-do-not-apply-yet")
  public Optional<ActIdentifier> at(String eId) {
    var exact = byOccurrence.get(eId);
    if (exact != null) {
      return Optional.of(exact);
    }
    return byOccurrence.entrySet().stream()
        .filter(each -> Amended.in(eId, each.getKey()) || Amended.in(each.getKey(), eId))
        .map(Map.Entry::getValue)
        .findFirst();
  }
}
