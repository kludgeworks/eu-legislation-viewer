// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A list: points, indents or definitions, as units. */
public record ListBlock(List<Provenance> provenance, Kind kind, List<Block> items)
    implements Block {

  public ListBlock {
    provenance = List.copyOf(provenance);
    items = List.copyOf(items);
  }

  /** How the items are numbered. */
  public enum Kind {
    ARABIC,
    ALPHA,
    ALPHA_UPPER,
    ROMAN,
    ROMAN_UPPER,
    DASH,
    NDASH,
    BULLET,
    NONE,
    OTHER,
    DEFINITION
  }
}
