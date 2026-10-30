// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** An image as a block, with its caption and a description of what it shows. */
public record Figure(
    String src, @Nullable ImageContent content, List<Block> caption, List<Block> description)
    implements Block {

  public Figure {
    caption = List.copyOf(caption);
    description = List.copyOf(description);
  }
}
