// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The feature "What the page shows": one function for each of its rules. A rule reads facts and
 * gives one new fact. It does not call another rule. {@link Summary#of} applies the rules of the
 * summary and the legend.
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

  /**
   * A difference that the page shows.
   *
   * @param difference the difference
   * @param correction whether it is a correction: only corrigenda made it
   */
  public record Shown(Difference difference, boolean correction) {}

  /**
   * The edits that the legend names.
   *
   * @param inserted whether the page shows inserted words
   * @param deleted whether the page shows deleted words
   * @param corrected whether the page shows the words of a correction
   */
  public record Legend(boolean inserted, boolean deleted, boolean corrected) {}

  /**
   * If one known act made every difference, the summary names it. If a difference has no known act,
   * or more than one act made the differences, the summary names no act.
   *
   * @param madeBy the acts that made each difference that the page shows. Empty for a difference
   *     with no known act.
   * @return the act that made every difference, or empty
   */
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "act-that-made-every-difference")
  public static Optional<ActIdentifier> actThatMadeEveryDifference(
      List<List<ActIdentifier>> madeBy) {
    var acts = new LinkedHashSet<ActIdentifier>();
    for (var each : madeBy) {
      if (each.isEmpty()) {
        return Optional.empty();
      }
      acts.addAll(each);
    }
    return acts.size() == 1 ? Optional.of(acts.getFirst()) : Optional.empty();
  }

  /**
   * The legend names inserted words, deleted words and the words of a correction, each only if the
   * page shows some. An inserted occurrence shows all its words inserted, and a deleted occurrence
   * shows all its words deleted. The inserted and deleted words of a correction count only as the
   * words of a correction.
   *
   * @param shown the differences that the page shows
   */
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "legend-names-the-edits-shown")
  public static Legend legendNamesTheEditsShown(List<Shown> shown) {
    var inserted = false;
    var deleted = false;
    var corrected = false;
    for (var each : shown) {
      var difference = each.difference();
      if (each.correction()) {
        corrected = true;
      } else {
        inserted |= difference.kind() == Kind.INSERTED || difference.inserted() > 0;
        deleted |= difference.kind() == Kind.DELETED || difference.deleted() > 0;
      }
    }
    return new Legend(inserted, deleted, corrected);
  }
}
