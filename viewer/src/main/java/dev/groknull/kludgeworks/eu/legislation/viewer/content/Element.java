// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * An element of a legal text, as it was stored. Its eIds and note references are as the converter
 * wrote them. Legal-text never mixes text with elements, so an element holds one or the other.
 *
 * @param name its name, as the legal-text JSON has it. The name is local, for example {@code p} or
 *     {@code unit}, or prefixed, for example {@code m:math}.
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
   * Returns its units from the unit {@code first} to the unit {@code last}, in order. This is a run
   * as a comparison folds it.
   *
   * <ul>
   *   <li>Each unit is whole, with the units in it. The units in it do not appear again on their
   *       own.
   *   <li>The run may start inside a unit and end outside it.
   *   <li>The run leaves out the units that a consolidation keeps struck, in {@code deleted}.
   * </ul>
   *
   * <p>Returns none if it does not have {@code first}.
   */
  public List<Element> units(String first, String last) {
    var run = new ArrayList<Element>();
    collect(this, first, last, Run.BEFORE, run);
    return run;
  }

  /** The position of a walk in a run of units. */
  private enum Run {
    BEFORE,
    IN,
    AFTER
  }

  /**
   * Adds the units of {@code element} that are in the run to {@code run}, from position {@code at}.
   * Returns the position after {@code element}.
   */
  private static Run collect(
      Element element, String first, String last, Run at, List<Element> run) {
    var now = at;
    for (var child : element.children()) {
      if (now == Run.AFTER) {
        return now;
      }
      // A consolidation keeps struck text here. It is not part of the text.
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
