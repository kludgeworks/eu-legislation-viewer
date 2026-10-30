// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Lines that hold blocks: a numbered paragraph with a second paragraph, a list, a table. */
public record BlockLines(List<Block> blocks) implements Lines {

  public BlockLines {
    blocks = List.copyOf(blocks);
  }
}
