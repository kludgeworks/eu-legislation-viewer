// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Blocks set together in a typeface or a box, or in another language (Formex HT or LV around whole
 * paragraphs, lists or levels: a boxed notice, a level inside a box), as AKN's blockContainer.
 * Inline emphasis stays emphasis; this is for what wraps blocks.
 *
 * @param emphasis The typeface or the box (Formex HT/@TYPE), as for inline emphasis.
 * @param lang The language of the blocks, when not the document's (Formex LV/@LG).
 */
public record BlockContainer(
    Emphasis.@Nullable Kind emphasis, @Nullable String lang, List<Block> content) implements Block {

  public BlockContainer {
    content = List.copyOf(content);
  }
}
