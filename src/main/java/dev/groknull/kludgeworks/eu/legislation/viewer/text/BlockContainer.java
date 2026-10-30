// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Blocks set together in a typeface or a box, or in another language. */
public record BlockContainer(
    Emphasis.@Nullable Kind emphasis, @Nullable String lang, List<Block> content) implements Block {

  public BlockContainer {
    content = List.copyOf(content);
  }
}
