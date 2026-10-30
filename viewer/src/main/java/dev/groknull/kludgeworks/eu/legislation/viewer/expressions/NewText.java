// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** The wording as corrected: "read:". */
public record NewText(List<Block> content) implements Block {

  public NewText {
    content = List.copyOf(content);
  }
}
