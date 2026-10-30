// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * Quoted text (Formex QUOT.START and QUOT.END in the same element). A pair that crosses elements
 * stays as plain quotation marks.
 *
 * @param startQuote The opening mark, e.g. ‘.
 * @param endQuote The closing mark, e.g. ’.
 */
public record Quote(String startQuote, String endQuote, List<Inline> content) implements Inline {

  public Quote {
    content = List.copyOf(content);
  }
}
