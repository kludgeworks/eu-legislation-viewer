// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.ArrayList;
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
 *     (t's v, number's v). Empty for any other element
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

  /**
   * Its units from the one {@code first} to the one {@code last}, in order, each whole, with the
   * units in it (not again on their own): a run as a comparison folds it, which may start inside a
   * unit and end outside it; not those a consolidation keeps struck (deleted). None if it hasn't
   * {@code first}.
   */
  public List<Element> units(String first, String last) {
    var run = new ArrayList<Element>();
    collect(this, first, last, Run.BEFORE, run);
    return run;
  }

  /** Where a walk is in a run of units. */
  private enum Run {
    BEFORE,
    IN,
    AFTER
  }

  /** {@code element}'s units in the run to {@code run}, from {@code at}; where it then is. */
  private static Run collect(
      Element element, String first, String last, Run at, List<Element> run) {
    var now = at;
    for (var child : element.children()) {
      if (now == Run.AFTER) {
        return now;
      }
      // what a consolidation keeps struck: not the text's
      if (child.name().equals("deleted")) {
        continue;
      }
      var eId = child.name().equals("unit") ? child.attribute("eId") : null;
      if (eId != null && eId.equals(first)) {
        now = Run.IN;
      }
      if (eId != null && now == Run.IN) {
        run.add(child);
        if (eId.equals(last) || contains(child, last)) {
          now = Run.AFTER;
        }
      } else {
        now = collect(child, first, last, now, run);
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
