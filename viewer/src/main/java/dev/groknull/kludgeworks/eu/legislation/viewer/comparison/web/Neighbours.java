// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds the neighbours of the differences of a comparison, as the Synopsis shows them. A neighbour
 * is an occurrence near a difference that is the same in both texts.
 *
 * <p>Each neighbour is the occurrence as the later text has it, with the amendment markers on its
 * own content. If its own content has no markers, it gets the markers of the nearest enclosing
 * occurrence that has a provenance of its own. An act put that enclosing occurrence in its current
 * form as a whole, with the occurrences in it. The Differences of a comparison get their markers in
 * the same way.
 */
final class Neighbours {
  private Neighbours() {}

  /**
   * An occurrence the same in both texts.
   *
   * @param occurrence the occurrence, as the later text has it, with the occurrences in it
   * @param markers the markers on its own content, or the markers of the nearest enclosing
   *     occurrence
   */
  record Neighbour(Element occurrence, List<Marker> markers) {
    Neighbour {
      markers = List.copyOf(markers);
    }
  }

  /** Returns {@code occurrence} as a row cites it: its eId, kind and num. */
  static Sibling sibling(Element occurrence) {
    var eId = occurrence.attribute("eId");
    var kind = occurrence.attribute("kind");
    var num = new StringBuilder();
    for (var child : occurrence.children()) {
      if (child.name().equals("num")) {
        said(child, num);
      }
    }
    return new Sibling(eId == null ? "" : eId, kind == null ? "" : kind, num.toString().strip());
  }

  private static void said(Element element, StringBuilder out) {
    var words = element.attribute("v");
    if (element.name().equals("t") && words != null) {
      out.append(words);
    }
    element.children().forEach(child -> said(child, out));
  }

  /**
   * Returns the occurrences {@code eIds} of {@code later}, by eId. An occurrence that {@code later}
   * does not have is not in the result.
   */
  static Map<String, Neighbour> of(Element later, Set<String> eIds) {
    var found = new HashMap<String, Neighbour>();
    walk(later, eIds, new ArrayDeque<>(), found);
    return found;
  }

  /**
   * Finds the neighbours in {@code element}, which is inside the occurrences {@code enclosing}. The
   * innermost enclosing occurrence is first.
   */
  private static void walk(
      Element element, Set<String> eIds, Deque<Element> enclosing, Map<String, Neighbour> found) {
    var eId = eId(element);
    if (eId != null && eIds.contains(eId)) {
      found.put(eId, new Neighbour(element, markers(element, enclosing)));
    }
    if (eId != null) {
      enclosing.push(element);
    }
    for (var child : element.children()) {
      walk(child, eIds, enclosing, found);
    }
    if (eId != null) {
      enclosing.pop();
    }
  }

  private static List<Marker> markers(Element occurrence, Deque<Element> enclosing) {
    var found = new LinkedHashSet<Marker>();
    own(occurrence, found);
    for (var each : enclosing) {
      if (!found.isEmpty()) {
        break;
      }
      for (var child : each.children()) {
        var act = child.attribute("amendingAct");
        var action = child.attribute("action");
        if (child.name().equals("provenance") && act != null && action != null) {
          found.add(new Marker(act, action, eId(each)));
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * Adds the markers on the own content of {@code element} to {@code found}. It does not add the
   * markers of the occurrences in it.
   */
  private static void own(Element element, Set<Marker> found) {
    for (var child : element.children()) {
      if (eId(child) != null) {
        continue;
      }
      var act = child.attribute("amendingAct");
      var action = child.attribute("action");
      if (act != null && action != null) {
        found.add(new Marker(act, action, null));
      }
      own(child, found);
    }
  }

  private static @Nullable String eId(Element element) {
    return element.name().equals("occurrence") ? element.attribute("eId") : null;
  }
}
