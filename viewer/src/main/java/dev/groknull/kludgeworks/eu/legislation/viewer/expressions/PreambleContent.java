// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** What a preamble holds, in order: its formulas, citations, recitals and other blocks. */
public sealed interface PreambleContent
    permits PreambleContent.Formula, PreambleContent.Citations, PreambleContent.Recitals, Block {

  /**
   * A fixed formula of the preamble.
   *
   * @param name Which formula this is.
   */
  record Formula(Name name, List<Block> content) implements PreambleContent {

    public Formula {
      content = List.copyOf(content);
    }

    /** The formulas of a preamble (AKN names). */
    public enum Name {
      /** Who adopts the act: "THE EUROPEAN COMMISSION,". */
      ACTING_ENTITY,
      /** The formula before the articles: "HAS ADOPTED THIS REGULATION:". */
      ENACTING_FORMULA
    }
  }

  /**
   * The citations of the preamble.
   *
   * @param intro Words before the first citation (Formex GR.VISA.INIT).
   */
  record Citations(List<Block> intro, List<Block> units) implements PreambleContent {

    public Citations {
      intro = List.copyOf(intro);
      units = List.copyOf(units);
    }
  }

  /**
   * The recitals of the preamble.
   *
   * @param intro The words before the first recital: "Whereas:".
   */
  record Recitals(List<Block> intro, List<Block> units) implements PreambleContent {

    public Recitals {
      intro = List.copyOf(intro);
      units = List.copyOf(units);
    }
  }
}
