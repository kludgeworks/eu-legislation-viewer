// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * The preamble: the acting entity, citations, recitals and the enacting formula, in source order.
 *
 * @param eId Always pbl_1.
 */
public record Preamble(EId eId, List<PreambleContent> content) {

  public Preamble {
    content = List.copyOf(content);
  }
}
