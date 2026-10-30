// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** In the long title: the act's number. */
public record DocNumber(List<Inline> content) implements Inline {

  public DocNumber {
    content = List.copyOf(content);
  }
}
