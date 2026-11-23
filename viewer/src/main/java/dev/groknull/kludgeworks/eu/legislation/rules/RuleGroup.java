// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.rules;

import java.util.Locale;

/**
 * The groups of the business rules: one for each Gherkin feature. A group's feature is the file of
 * its name in kebab case, in the folder of the module whose domain applies the rules. For example,
 * {@link #WHO_MADE_A_CHANGE} is {@code comparison/who-made-a-change.feature}.
 */
public enum RuleGroup {
  /** Which acts made a difference, in which steps, and with which modification. */
  WHO_MADE_A_CHANGE,
  /** From when the change of an act applies. */
  WHEN_A_CHANGE_APPLIES,
  /** How the occurrences of two texts pair. */
  HOW_TWO_TEXTS_PAIR,
  /** What counts as a difference, and how its edits show. */
  WHAT_COUNTS_AS_A_DIFFERENCE,
  /** What the summary and the legend of a comparison say. */
  WHAT_THE_PAGE_SHOWS,
  /** The neighbours of a difference. */
  NEIGHBOURS,
  /** The changes of one act. */
  THE_CHANGES_OF_ONE_ACT,
  /** From when an act, or a provision of it, applies, as its dates in Cellar tell. */
  WHEN_AN_ACT_APPLIES,
  /** The state of an act, or of one change, on a day, and how an amending act stands. */
  THE_STATE_ON_A_DAY,
  /** Where the text that a reader gets on a day is not the law on that day, and why. */
  THE_TEXT_AND_THE_LAW_ON_A_DAY,
  /** Which occurrences of a text the place of an amendment is. */
  PLACES;

  /** Returns the name of the group's feature file, without its extension. */
  public String feature() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
