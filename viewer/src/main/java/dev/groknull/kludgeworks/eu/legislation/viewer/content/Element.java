// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An element of a legal text, as it was stored. Its eIds and footnote references are as the
 * converter wrote them. Legal-text never mixes text with elements, so an element holds one or the
 * other.
 *
 * @param name its name, as the legal-text JSON has it. The name is local, for example {@code p} or
 *     {@code occurrence}, or prefixed, for example {@code m:math}.
 * @param attributes its attributes, by the same names. For example, {@code eId} or {@code
 *     xml:lang}.
 * @param children its child elements, in order. None for an element of text.
 * @param text its text. Only the MathML tokens, for example {@code mi} and {@code mo}, hold text.
 *     Legal-text keeps its own text in attributes, for example the {@code v} of {@code t} or of
 *     {@code number}. Empty for any other element.
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
   * Returns its attribute {@code name}. Returns null if it has none, so that a template leaves out
   * the HTML attribute.
   */
  public @Nullable String attribute(String name) {
    return attributes.get(name);
  }

  /**
   * Returns its occurrences from the occurrence {@code first} to the occurrence {@code last}, in
   * order, as a fold of a comparison holds them.
   *
   * <ul>
   *   <li>Each occurrence is whole, with the occurrences in it. The occurrences in it do not appear
   *       again on their own.
   *   <li>They may start inside an occurrence and end outside it.
   *   <li>They leave out the occurrences of a consolidation's kept deletions, in {@code deleted}.
   * </ul>
   *
   * <p>Returns none if it does not have {@code first}.
   */
  public List<Element> occurrences(String first, String last) {
    var found = new ArrayList<Element>();
    collect(this, first, last, Position.BEFORE, found);
    return found;
  }

  /** Where a walk is: before {@code first}, between {@code first} and {@code last}, or after. */
  private enum Position {
    BEFORE,
    IN,
    AFTER
  }

  /**
   * Adds the occurrences of {@code element} that are between {@code first} and {@code last} to
   * {@code found}, from position {@code at}. Returns the position after {@code element}.
   */
  private static Position collect(
      Element element, String first, String last, Position at, List<Element> found) {
    var now = at;
    for (var child : element.children()) {
      if (now == Position.AFTER) {
        return now;
      }
      // A consolidation keeps its kept deletions here. They are not part of the text.
      if (child.name().equals("deleted")) {
        continue;
      }
      var eId = child.name().equals("occurrence") ? child.attribute("eId") : null;
      if (eId != null && eId.equals(first)) {
        now = Position.IN;
      }
      if (eId != null && now == Position.IN) {
        found.add(child);
        if (eId.equals(last) || contains(child, last)) {
          now = Position.AFTER;
        }
      } else {
        now = collect(child, first, last, now, found);
      }
    }
    return now;
  }

  private static boolean contains(Element element, String eId) {
    for (var child : element.children()) {
      if (eId.equals(child.attribute("eId")) || contains(child, eId)) {
        return true;
      }
    }
    return false;
  }
}
