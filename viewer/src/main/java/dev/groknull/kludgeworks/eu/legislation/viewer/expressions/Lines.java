// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * A heading, a subheading or a signatory's line: inline content, several lines joined by eol. Where
 * the source's lines hold blocks (a numbered paragraph with a second paragraph, a list, a table, an
 * image with its caption, a signature in each language), the blocks instead, in content.
 */
public sealed interface Lines {

  /** Lines of inline content, joined by {@link Eol}. */
  record InlineLines(List<Inline> content) implements Lines {

    public InlineLines {
      content = List.copyOf(content);
    }
  }

  /** Lines that hold blocks: a numbered paragraph with a second paragraph, a list, a table. */
  record BlockLines(List<Block> blocks) implements Lines {

    public BlockLines {
      blocks = List.copyOf(blocks);
    }
  }
}
