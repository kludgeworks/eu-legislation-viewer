// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A provision that differs between two texts, as the Changes view shows it (Design: Mode: Compare):
 * a unit, by its eId, inserted, deleted, or with its own wording changed (its num, heading and
 * blocks, not its units', which differ on their own). An inserted or deleted unit is one difference
 * with its units, not one each.
 *
 * <p>It is shown as legal-text, marked: its words inserted in diff:ins, its words deleted in
 * diff:del, only ever around text (t, number), so each element keeps the parent it has in the text
 * and renders as it reads. A changed unit is shown with its own content only, the later text's, the
 * earlier's words deleted where they stood; an inserted one whole, every word inserted; a deleted
 * one whole, as the earlier text had it, every word deleted.
 *
 * @param eId the unit's eId
 * @param kind how it differs
 * @param shown the unit as the view shows it, marked
 * @param inserted how many words it has inserted
 * @param deleted how many words it has deleted
 * @param parent the eId of the unit it is in; none for an outermost unit
 * @param lead the opening of the unit it is in, as context: its num, heading and first block,
 *     unmarked; none for an outermost unit
 * @param folded the units before it in the unit it is in, back to the one before that differs or
 *     holds a difference, which are the same in both texts; none for a deleted unit
 */
@ValueObject
@RecordBuilder
public record Difference(
    String eId,
    Kind kind,
    Element shown,
    int inserted,
    int deleted,
    @Nullable String parent,
    @Nullable Element lead,
    List<Sibling> folded) {

  public Difference {
    folded = List.copyOf(folded);
  }

  /** How a provision differs. */
  public enum Kind {
    /** Only in the later text. */
    INSERTED,
    /** Only in the earlier text. */
    DELETED,
    /** In both, its wording changed. */
    CHANGED
  }

  /**
   * A unit the same in both texts, as a fold names it: Points (a) and (b).
   *
   * @param eId its eId
   * @param kind its kind, as legal-text has it: article, point, level…
   * @param num its num, as written: (a), 8.1.2.3; empty for one without
   */
  @ValueObject
  public record Sibling(String eId, String kind, String num) {}
}
