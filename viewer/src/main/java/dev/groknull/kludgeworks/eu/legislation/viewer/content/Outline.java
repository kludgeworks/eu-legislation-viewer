// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The outline of a text.
 *
 * <p>It holds the occurrences of the text that have a title, in order. These are the occurrences
 * with a rank in legal-text: articles, annexes, divisions and the headed levels of an annex. Each
 * occurrence holds the occurrences ranked below it, until the next occurrence of its rank or
 * higher. The levels of an annex, which legal-text keeps flat, also nest by their rank.
 *
 * <p>It also holds the eIds of all its occurrences, with a title or not. These find an occurrence
 * for a {@code Location}.
 *
 * <p>It also holds the tags of the occurrences. A consolidated text tags the wording that an act
 * gave, with the act. For example, the version of 2015/1998 of 7 June 2017 tags point 4.1.3.3 of
 * its annex with 2017/815.
 *
 * @param entries the outermost occurrences
 * @param eIds the eIds of all its occurrences and other containers that have one, in document order
 * @param tags the acts that each occurrence tags, by the eId of the occurrence. A tag is on the
 *     occurrence, or on a block in it that has no eId of its own. An act is its CELEX, for example
 *     {@code 32017R0815}. An occurrence without tags is left out.
 */
@ValueObject
public record Outline(List<Entry> entries, List<String> eIds, Map<String, Set<String>> tags) {

  public Outline {
    entries = List.copyOf(entries);
    eIds = List.copyOf(eIds);
    tags = Map.copyOf(tags);
  }

  /** Returns the outline of a text that has no occurrences. */
  public static Outline empty() {
    return new Outline(List.of(), List.of(), Map.of());
  }

  /**
   * An occurrence in the outline.
   *
   * @param eId its eId, where the text has it. For example, {@code art_3} or {@code anx/pnt_1.2}.
   * @param kind its kind in legal-text. For example, article, annex, division or level.
   * @param num its label as printed. For example, "Article 3", "ANNEX" or "1.2". Empty if it has
   *     none.
   * @param heading its heading, with its lines joined by spaces. Empty if it has none.
   * @param entries the occurrences ranked below it, in order
   */
  @ValueObject
  public record Entry(String eId, String kind, String num, String heading, List<Entry> entries) {

    public Entry {
      entries = List.copyOf(entries);
    }
  }
}
