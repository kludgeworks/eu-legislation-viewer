// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** In a correction, the wording as published: "for:". */
public record OldText(List<Block> content) implements Block {

  public OldText {
    content = List.copyOf(content);
  }
}
