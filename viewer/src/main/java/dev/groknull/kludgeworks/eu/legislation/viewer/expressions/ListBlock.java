// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * A list of points or indents.
 *
 * @param provenance The amending acts that put this list in its current form, when they differ from
 *     its unit's.
 * @param kind How the items are numbered.
 */
public record ListBlock(List<Provenance> provenance, Kind kind, List<Block> items)
    implements Block {

  public ListBlock {
    provenance = List.copyOf(provenance);
    items = List.copyOf(items);
  }

  /** How the items of a list are numbered (Formex LIST/@TYPE). */
  public enum Kind {
    /** 1, 2, 3 (ARAB). */
    ARABIC,
    /** a, b, c (alpha). */
    ALPHA,
    /** A, B, C (ALPHA). */
    ALPHA_UPPER,
    /** i, ii, iii (roman). */
    ROMAN,
    /** I, II, III (ROMAN). */
    ROMAN_UPPER,
    /** Em dashes (DASH). */
    DASH,
    /** En dashes (NDASH, Formex 05.59 and later). */
    NDASH,
    /** Bullets (BULLET). */
    BULLET,
    /** No marks (NONE). */
    NONE,
    /** Marks the source writes out in each item (OTHER). */
    OTHER,
    /** A definition list (Formex DLIST). */
    DEFINITION
  }
}
