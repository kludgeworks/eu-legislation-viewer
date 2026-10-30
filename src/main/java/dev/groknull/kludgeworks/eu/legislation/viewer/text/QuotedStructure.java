// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Text an amending act quotes: what it inserts or substitutes. */
public record QuotedStructure(EId eId, boolean ambiguous, List<Block> content) implements Block {

  public QuotedStructure {
    content = List.copyOf(content);
  }
}
