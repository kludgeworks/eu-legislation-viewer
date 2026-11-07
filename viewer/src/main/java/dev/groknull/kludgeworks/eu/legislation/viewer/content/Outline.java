// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The outline of a text.
 *
 * <p>It holds the units of the text that have a title, in order. These are the units with a rank in
 * legal-text: articles, annexes, divisions and the headed levels of an annex. Each unit holds the
 * units ranked below it, until the next unit of its rank or higher. The levels of an annex, which
 * legal-text keeps flat, also nest by their rank.
 *
 * <p>It also holds the eIds of all its units, with a title or not. These find a unit for a {@code
 * Location}.
 *
 * @param entries the outermost units
 * @param eIds the eIds of all its units and other containers that have one, in document order
 */
@ValueObject
public record Outline(List<Entry> entries, List<String> eIds) {

  public Outline {
    entries = List.copyOf(entries);
    eIds = List.copyOf(eIds);
  }

  /** Returns the outline of a text that has no units. */
  public static Outline empty() {
    return new Outline(List.of(), List.of());
  }

  /**
   * A unit in the outline.
   *
   * @param eId its eId, where the text has it. For example, {@code art_3} or {@code anx/pnt_1.2}.
   * @param kind its kind in legal-text. For example, article, annex, division or level.
   * @param num its label as printed. For example, "Article 3", "ANNEX" or "1.2". Empty if it has
   *     none.
   * @param heading its heading, with its lines joined by spaces. Empty if it has none.
   * @param entries the units ranked below it, in order
   */
  @ValueObject
  public record Entry(String eId, String kind, String num, String heading, List<Entry> entries) {

    public Entry {
      entries = List.copyOf(entries);
    }
  }
}
