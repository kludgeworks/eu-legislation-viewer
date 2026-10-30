// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/** In the long title: the number, "(EU) 2015/1998". */
public record DocNumber(List<Inline> content) implements Inline {

  public DocNumber {
    content = List.copyOf(content);
  }
}
