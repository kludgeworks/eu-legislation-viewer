// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A text's outline: its units with a title (legal-text's rank: articles, annexes, divisions, an
 * annex's headed levels), in order, each with the units ranked below it until the next of its rank
 * or above. Annex levels, which legal-text keeps flat, nest by their rank too. And the eIds of all
 * its units, titled or not, to find a unit by (Location).
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

  /** The outline of a text that has none: no units. */
  public static Outline empty() {
    return new Outline(List.of(), List.of());
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
