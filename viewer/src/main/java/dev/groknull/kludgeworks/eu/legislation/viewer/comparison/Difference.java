// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A provision that differs between two texts: a unit, by its eId, inserted, deleted, or with its
 * own wording changed (its num, heading and blocks, not its units', which differ on their own). An
 * inserted or deleted unit is one difference with its units, not one each.
 *
 * @param eId the unit's eId
 * @param kind how it differs
 * @param blocks for a changed unit, how its blocks differ, in order; none for one inserted or
 *     deleted, which is wholly so
 */
@ValueObject
public record Difference(String eId, Kind kind, List<Block> blocks) {
  public Difference {
    blocks = List.copyOf(blocks);
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
   * One of a changed unit's own blocks that differs, by its position among them in each text.
   *
   * @param from its position in the earlier text; none for a block inserted
   * @param to its position in the later text; none for a block deleted
   * @param runs its wording, in runs as each reads: the same in both, inserted or deleted
   */
  @ValueObject
  public record Block(@Nullable Integer from, @Nullable Integer to, List<Run> runs) {
    public Block {
      runs = List.copyOf(runs);
    }
  }

  /**
   * A run of words.
   *
   * @param mark how it is marked: the same in both texts, or only in the later or the earlier
   * @param text its words, with the spaces and punctuation between them
   */
  @ValueObject
  public record Run(Mark mark, String text) {

    /** Which texts have a run. */
    public enum Mark {
      SAME,
      INSERTED,
      DELETED
    }
  }
}
