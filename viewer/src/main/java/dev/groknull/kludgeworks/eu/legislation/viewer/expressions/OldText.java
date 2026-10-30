// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** The wording as published: "for:". */
public record OldText(List<Block> content) implements Block {

  public OldText {
    content = List.copyOf(content);
  }
}
