// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An element of a legal text, as it was stored: its eIds and note references as the converter wrote
 * them. Legal-text never mixes text with elements, so an element holds one or the other.
 *
 * @param name its name, as legal-text's JSON has it: local (p, unit) or prefixed (m:math)
 * @param attributes its attributes, by the same names (eId, xml:lang)
 * @param children its child elements, in order; none for an element of text
 * @param text its text, which only MathML's tokens hold (mi, mo): legal-text's own is in attributes
 *     (t's v, a date's asWritten). Empty for any other element
 */
@ValueObject
public record Element(
    String name, Map<String, String> attributes, List<Element> children, String text) {

  public Element {
    attributes = Map.copyOf(attributes);
    children = List.copyOf(children);
    if (!children.isEmpty() && !text.isEmpty()) {
      throw new IllegalArgumentException("Text beside elements in " + name);
    }
  }

  /**
   * Its attribute {@code name}; none if it has none (so a template leaves the HTML attribute out).
   */
  public @Nullable String attribute(String name) {
    return attributes.get(name);
  }
}
