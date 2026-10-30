// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** Lines of inline content, joined by {@link Eol}. */
public record InlineLines(List<Inline> content) implements Lines {

  public InlineLines {
    content = List.copyOf(content);
  }
}
