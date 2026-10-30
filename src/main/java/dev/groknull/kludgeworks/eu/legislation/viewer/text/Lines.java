// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * A heading, subheading or signatory line: inline content, several lines joined by {@link Eol}; or,
 * where the source's lines hold blocks, those blocks instead.
 */
public record Lines(List<Inline> content, List<Block> blocks) {

  public Lines {
    content = List.copyOf(content);
    blocks = List.copyOf(blocks);
  }
}
