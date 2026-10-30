// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** The preamble: formulas, citations and recitals in their order. */
public record Preamble(EId eId, List<PreambleContent> content) {

  public Preamble {
    content = List.copyOf(content);
  }
}
