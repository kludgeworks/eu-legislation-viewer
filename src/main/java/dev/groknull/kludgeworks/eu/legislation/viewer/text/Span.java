// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * Text in another language than the document's (Formex LV): a term quoted in its original language,
 * or the multilingual closing formulas of an agreement.
 *
 * @param lang The language of the text, e.g. fr.
 */
public record Span(String lang, List<Inline> content) implements Inline {

  public Span {
    content = List.copyOf(content);
  }
}
