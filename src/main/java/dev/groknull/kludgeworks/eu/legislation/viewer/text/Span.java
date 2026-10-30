// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Text in another language. */
public record Span(String lang, List<Inline> content) implements Inline {

  public Span {
    content = List.copyOf(content);
  }
}
