// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Text set in a typeface: bold, italic, superscript and so on. */
public record Emphasis(Kind kind, List<Inline> content) implements Inline {

  public Emphasis {
    content = List.copyOf(content);
  }

  /** The typeface. */
  public enum Kind {
    B,
    I,
    U,
    SUP,
    SUB,
    UC,
    SC,
    EXPANDED,
    STRIKE,
    BOX
  }
}
