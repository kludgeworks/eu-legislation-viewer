// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;

/**
 * The feature "What the page shows": one function for each of its rules. A rule reads facts and
 * gives one new fact. It does not call another rule. The rules act-that-made-every-difference and
 * legend-names-the-edits-shown are {@link Summary#of}.
 */
public final class WhatThePageShows {

  private WhatThePageShows() {}

  /** How the Differences view places an occurrence of the later text. */
  public enum Placed {
    /** As a difference. */
    DIFFERENCE,
    /** As the opening of an occurrence that holds differences. */
    OPENING,
    /** Among the occurrences that are the same in both texts, which a fold holds. */
    IN_A_FOLD
  }

  /**
   * The Differences view places each occurrence of the later text once, in the order of the text:
   * as a difference, as the opening of an occurrence that holds differences, or among the
   * occurrences that are the same in both texts, which a fold holds.
   *
   * @param isADifference whether the occurrence itself is a difference
   * @param holdsDifferences whether an occurrence in it is a difference
   * @return how the view places the occurrence
   */
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "each-occurrence-once")
  public static Placed eachOccurrenceOnce(boolean isADifference, boolean holdsDifferences) {
    if (isADifference) {
      return Placed.DIFFERENCE;
    }
    return holdsDifferences ? Placed.OPENING : Placed.IN_A_FOLD;
  }
}
