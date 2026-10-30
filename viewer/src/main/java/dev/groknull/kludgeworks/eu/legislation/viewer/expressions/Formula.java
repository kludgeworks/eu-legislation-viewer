// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * A fixed formula of the preamble.
 *
 * @param name Which formula this is.
 */
public record Formula(Name name, List<Block> content) implements PreambleContent {

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
