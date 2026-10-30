// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A level of the hierarchy: an article, paragraph, point, annex, recital and so on, by its kind.
 */
public record Unit(
    EId eId,
    Kind kind,
    List<Provenance> provenance,
    List<Inline> num,
    @Nullable Lines heading,
    @Nullable Lines subheading,
    List<Block> content,
    @Nullable Integer depth,
    boolean box,
    boolean ambiguous,
    @Nullable Integer eurlexAnchor)
    implements Block {

  public Unit {
    provenance = List.copyOf(provenance);
    num = List.copyOf(num);
    content = List.copyOf(content);
  }

  /** What the unit is. */
  public enum Kind {
    DIVISION,
    SUBDIVISION,
    ARTICLE,
    PARAGRAPH,
    SUBPARAGRAPH,
    POINT,
    INDENT,
    DEFINITION,
    LEVEL,
    ANNEX,
    CORRECTION,
    ATTACHMENT,
    LETTER,
    CITATION,
    RECITAL
  }
}
