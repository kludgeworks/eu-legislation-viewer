// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import java.util.LinkedHashSet;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * What the differences that a page shows have in them, for its summary and its legend.
 *
 * <ul>
 *   <li>The legend names only the edits that the page shows: inserted words, deleted words, and the
 *       words of a correction.
 *   <li>The words of a correction count only as a correction, not as inserted or deleted words.
 *   <li>If one known act made every difference, the summary names it. If a difference has no known
 *       act, or two acts made the differences, it names none.
 * </ul>
 *
 * @param inserted whether the page shows inserted words
 * @param deleted whether the page shows deleted words
 * @param corrected whether the page shows the words of a correction
 * @param by the one act that made every difference. Null if there is none.
 */
@ValueObject
public record Summary(
    boolean inserted, boolean deleted, boolean corrected, @Nullable ActIdentifier by) {

  /**
   * Returns the summary of the differences {@code shown}, with who made each ({@link Attributed}),
   * in the order that the page shows them.
   */
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "act-that-made-every-difference")
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "legend-names-the-edits-shown")
  public static Summary of(List<Attributed> shown) {
    var inserted = false;
    var deleted = false;
    var corrected = false;
    var acts = new LinkedHashSet<ActIdentifier>();
    var known = true;
    for (var each : shown) {
      var difference = each.difference();
      var attribution = each.attribution();
      if (attribution.corrected()) {
        corrected = true;
      } else {
        inserted |= difference.kind() == Kind.INSERTED || difference.inserted() > 0;
        deleted |= difference.kind() == Kind.DELETED || difference.deleted() > 0;
      }
      if (attribution.acts().isEmpty()) {
        known = false;
      } else {
        acts.addAll(attribution.acts());
      }
    }
    return new Summary(
        inserted, deleted, corrected, known && acts.size() == 1 ? acts.getFirst() : null);
  }
}
