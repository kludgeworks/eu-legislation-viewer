// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A text's outline: its units with a title (legal-text's rank: articles, annexes, divisions, an
 * annex's headed levels), in order, each with the units ranked below it until the next of its rank
 * or above. Annex levels, which legal-text keeps flat, nest by their rank too.
 *
 * @param entries the outermost units
 */
@ValueObject
public record Outline(List<Entry> entries) {

  public Outline {
    entries = List.copyOf(entries);
  }

  /**
   * A unit in the outline.
   *
   * @param eId its eId, where the text has it (art_3, anx/pnt_1.2)
   * @param kind legal-text's kind (article, annex, division, level)
   * @param num its label as printed ("Article 3", "ANNEX", "1.2"); empty if it has none
   * @param heading its heading, its lines joined by spaces; empty if it has none
   * @param entries the units ranked below it, in order
   */
  @ValueObject
  public record Entry(String eId, String kind, String num, String heading, List<Entry> entries) {

    public Entry {
      entries = List.copyOf(entries);
    }
  }
}
