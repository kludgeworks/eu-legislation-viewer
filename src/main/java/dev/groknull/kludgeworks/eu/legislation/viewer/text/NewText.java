// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** In a correction, the wording as corrected: "read:". */
public record NewText(List<Block> content) implements Block {

  public NewText {
    content = List.copyOf(content);
  }
}
