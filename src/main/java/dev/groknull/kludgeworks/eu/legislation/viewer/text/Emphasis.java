// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * Text set in a typeface (Formex HT).
 *
 * @param kind Which typeface.
 */
public record Emphasis(Kind kind, List<Inline> content) implements Inline {

  public Emphasis {
    content = List.copyOf(content);
  }

  /** Typefaces (Formex HT/@TYPE). */
  public enum Kind {
    /** Bold. */
    B,
    /** Italic. */
    I,
    /** Underlined. */
    U,
    /** Superscript: m². */
    SUP,
    /** Subscript. */
    SUB,
    /** Upper case; surnames in signatures. */
    UC,
    /** Small capitals (Formex 05.59 and later). */
    SC,
    /** Letter-spaced. */
    EXPANDED,
    /** Struck through. */
    STRIKE,
    /** Enclosed in a box. */
    BOX
  }
}
