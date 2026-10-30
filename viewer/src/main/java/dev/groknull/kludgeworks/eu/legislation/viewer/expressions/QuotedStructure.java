// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * The units an amending act inserts or substitutes (Formex QUOT.S). The instruction ("point 1.2.2.3
 * is replaced by the following:") is the paragraph before it in the same unit.
 *
 * @param eId The enclosing unit's eId plus qstr_N; the quoted units' eIds hang off it.
 * @param ambiguous Inside a unit marked ambiguous, whose eId this one shares through it.
 */
public record QuotedStructure(EId eId, boolean ambiguous, List<Block> content) implements Block {

  public QuotedStructure {
    content = List.copyOf(content);
  }
}
