// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
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
 * The neighbours of a comparison's differences, the units around them the same in both texts, as
 * the Synopsis shows them (Design: Mode: Compare, Synopsis): each as the later text has it, with
 * the amendment markers on its own content; where it has none, those of the nearest unit it is in
 * with a provenance of its own, which an act put in its form whole, with the units in it (as the
 * comparison's Differences have theirs).
 */
final class Neighbours {
  private Neighbours() {}

  /**
   * A unit the same in both texts.
   *
   * @param unit the unit, as the later text has it, with the units in it
   * @param markers the markers on its own content, or the nearest enclosing unit's
   */
  record Neighbour(Element unit, List<Marker> markers) {
    Neighbour {
      markers = List.copyOf(markers);
    }
  }

  /** The units {@code eIds} of {@code later}, by eId; those it hasn't left out. */
  static Map<String, Neighbour> of(Element later, Set<String> eIds) {
    var found = new HashMap<String, Neighbour>();
    walk(later, eIds, new ArrayDeque<>(), found);
    return found;
  }

  /** {@code element}, inside the units {@code enclosing} (the innermost first). */
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

  private static List<Marker> markers(Element unit, Deque<Element> enclosing) {
    var found = new LinkedHashSet<Marker>();
    own(unit, found);
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

  /** The markers on {@code element}'s own content: not those of the units in it. */
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
    return element.name().equals("unit") ? element.attribute("eId") : null;
  }
}
