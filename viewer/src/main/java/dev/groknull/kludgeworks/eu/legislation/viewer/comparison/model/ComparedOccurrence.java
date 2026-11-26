// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.List;
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
}
