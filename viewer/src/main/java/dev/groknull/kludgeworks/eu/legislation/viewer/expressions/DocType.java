// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** In the long title: the type of act, "Commission Implementing Regulation". */
public record DocType(List<Inline> content) implements Inline {

  public DocType {
    content = List.copyOf(content);
  }
}
