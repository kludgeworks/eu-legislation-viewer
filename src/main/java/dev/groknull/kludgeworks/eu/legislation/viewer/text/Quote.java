// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Quoted text with its quotation marks. */
public record Quote(String startQuote, String endQuote, List<Inline> content) implements Inline {

  public Quote {
    content = List.copyOf(content);
  }
}
