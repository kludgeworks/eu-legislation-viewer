// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An occurrence of the two texts, compared.
 *
 * @param occurrence the occurrence, as the later text has it. For a deleted occurrence, as the
 *     earlier text has it.
 * @param earlier whether the occurrence is of only the earlier text: it is deleted
 * @param difference how the occurrence differs. Null if its own wording is the same in both texts.
 * @param editing how its own blocks and footnotes differ. Null if the occurrence is not changed.
 * @param occurrences the occurrences in it, compared, in the one order of the two texts. Empty for
 *     an inserted or deleted occurrence, which is one difference with its occurrences.
 * @param differs whether the occurrence differs, or an occurrence in it does
 */
record ComparedOccurrence(
    Occurrence occurrence,
    boolean earlier,
    @Nullable DifferenceBuilder difference,
    @Nullable OccurrenceEdits editing,
    List<ComparedOccurrence> occurrences,
    boolean differs) {

  static ComparedOccurrence of(
      Occurrence occurrence,
      boolean earlier,
      @Nullable DifferenceBuilder difference,
      @Nullable OccurrenceEdits editing,
      List<ComparedOccurrence> occurrences) {
    var differs = difference != null || occurrences.stream().anyMatch(ComparedOccurrence::differs);
    return new ComparedOccurrence(occurrence, earlier, difference, editing, occurrences, differs);
  }

  /**
   * Returns whether the occurrence shows whole, as one difference with the occurrences in it that
   * differ. It does if it is changed, and the acts that changed it also made every difference in
   * it.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "same-acts-one-difference")
  boolean showsWhole() {
    return difference != null
        && difference.kind() == Kind.CHANGED
        && occurrences.stream().anyMatch(ComparedOccurrence::differs)
        && madeBy(acts(difference));
  }

  /** Returns whether {@code acts} made every difference in the occurrence. */
  private boolean madeBy(Set<String> acts) {
    for (var each : occurrences) {
      var in = each.difference();
      if ((in != null && !acts(in).equals(acts)) || !each.madeBy(acts)) {
        return false;
      }
    }
    return true;
  }

  /** Returns how many words are inserted in the occurrence and the occurrences in it. */
  int inserted() {
    var own = difference == null ? 0 : difference.inserted();
    return own + occurrences.stream().mapToInt(ComparedOccurrence::inserted).sum();
  }

  /** Returns how many words are deleted in the occurrence and the occurrences in it. */
  int deleted() {
    var own = difference == null ? 0 : difference.deleted();
    return own + occurrences.stream().mapToInt(ComparedOccurrence::deleted).sum();
  }

  /**
   * Returns the occurrence as it shows in an occurrence that shows whole. An inserted or deleted
   * occurrence shows whole, as an insertion or a deletion. Any other occurrence shows its edits
   * where it and the occurrences in it differ.
   */
  Element within() {
    return difference != null && difference.kind() != Kind.CHANGED
        ? difference.shown()
        : ShownOccurrence.of(this, true);
  }

  /**
   * Returns the acts that made {@code difference}, from its tags. Returns an empty set if it has no
   * tags.
   */
  private static Set<String> acts(DifferenceBuilder difference) {
    var acts = new HashSet<String>();
    for (var tag : difference.tags()) {
      acts.add(tag.act());
    }
    return acts;
  }
}
