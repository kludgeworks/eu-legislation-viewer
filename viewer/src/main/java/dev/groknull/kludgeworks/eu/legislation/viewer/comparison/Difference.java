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
 * @param unit the unit as a fold names it, as the later text has it (the earlier, for a deleted
 *     one): where the Changes view folds it with others an act filter leaves out
 * @param shown the unit as the view shows it, marked
 * @param inserted how many words it has inserted
 * @param deleted how many words it has deleted
 * @param parent the eId of the unit it is in; none for an outermost unit
 * @param section the eId of the unit with a rank it is in, as the text's outline nests them
 *     (anx/pnt_8.1.2 for anx/pnt_8.1.2.3/pnt_c); itself if it has a rank; none if it is in none
 * @param lead the opening of the unit it is in: its num, heading and first block, unmarked; none
 *     for an outermost unit
 * @param context what the later text has before it, after the difference before it (from its start,
 *     for the first), in order: the opening of each unit holding differences it enters, and the
 *     units the same in both, folded; with the comparison's closing, each of the later text's units
 *     once, but those that differ
 * @param markers the amendment markers on the unit's own content in the later text: which acts put
 *     it in the form it has there, and how; where it has none, those on the nearest unit it is in
 *     that an act put in its form whole; none for a deleted unit
 * @param earlierMarkers the same in the earlier text; none for an inserted unit
 */
@ValueObject
@RecordBuilder
public record Difference(
    String eId,
    Kind kind,
    Sibling unit,
    Element shown,
    int inserted,
    int deleted,
    @Nullable String parent,
    @Nullable String section,
    @Nullable Element lead,
    List<Context> context,
    List<Marker> markers,
    List<Marker> earlierMarkers) {

  public Difference {
    context = List.copyOf(context);
    markers = List.copyOf(markers);
    earlierMarkers = List.copyOf(earlierMarkers);
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
   * What the later text has between two differences, as the Changes view sets it out: the opening
   * of a unit that holds differences, where they enter it, or a run of units in one unit that are
   * the same in both texts, each with its own units, folded.
   *
   * @param units the units folded, in order; or the unit opened, alone
   * @param parent the eId of the unit they are in; none for outermost units
   * @param opening the opening of the unit opened: its num, heading and first block; none for a
   *     fold
   */
  @ValueObject
  public record Context(List<Sibling> units, @Nullable String parent, @Nullable Element opening) {
    public Context {
      units = List.copyOf(units);
    }
  }

  /**
   * A unit as a fold names it: Points (a) and (b).
   *
   * @param eId its eId
   * @param kind its kind, as legal-text has it: article, point, level…
   * @param num its num, as written: (a), 8.1.2.3; empty for one without
   */
  @ValueObject
  public record Sibling(String eId, String kind, String num) {}

  /**
   * An amendment marker on a unit's content, as the consolidation has it (▼M20, which the converter
   * keeps as provenance, changed or deleted): the act that put it in its form there, and what it
   * did.
   *
   * @param act the amending act's CELEX: 32026R0449
   * @param action what it did, as legal-text has it: replaced, inserted, deleted, renumbered
   * @param in the eId of the unit it is in that the act put in its form whole (2019/103 replaced
   *     section 11.1, with its points), where the unit has no marker of its own; none for its own
   */
  @ValueObject
  public record Marker(String act, String action, @Nullable String in) {}
}
