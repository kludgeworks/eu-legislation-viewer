// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the amendment markers on the own content of a unit.
 *
 * <p>The converter keeps the markers of a consolidation as legal-text {@code provenance}, {@code
 * changed} and {@code deleted}. Each names its amending act and what the act did. The own markers
 * of a unit are those of the unit and its blocks, not those of the units in it. Each marker is
 * returned once, in the order in which they stand.
 *
 * <p>If a unit has no markers of its own, it takes the provenance of the nearest enclosing unit
 * that has a provenance of its own. An act that replaced or inserted that unit whole did so with
 * the units in it.
 */
final class Markers {
  private Markers() {}

  /**
   * Returns the markers on the own content of {@code unit}, in {@code units}. If it has none,
   * returns the provenance of the nearest enclosing unit that has one. Those markers name that
   * enclosing unit in {@code in}.
   */
  static List<Marker> of(Unit unit, Units units) {
    var found = new LinkedHashSet<Marker>();
    collect(unit.element(), found);
    for (var at = unit.parent(); found.isEmpty() && at != null; ) {
      var enclosing = units.get(at);
      if (enclosing == null) {
        break;
      }
      for (var child : enclosing.element().children()) {
        var act = child.attribute("amendingAct");
        var action = child.attribute("action");
        if (child.name().equals("provenance") && act != null && action != null) {
          found.add(new Marker(act, action, enclosing.eId()));
        }
      }
      at = enclosing.parent();
    }
    return List.copyOf(found);
  }

  /**
   * Returns the markers of the act that deleted {@code unit} of the earlier text. The later text
   * {@code after} tells which act it was.
   *
   * <ul>
   *   <li>If a deletion keeps the unit struck, it returns the markers of that deletion.
   *   <li>If not, it returns the provenance of the nearest enclosing unit that an act replaced
   *       whole, without the unit. Those markers name that enclosing unit in {@code in}.
   *   <li>If neither applies, it returns an empty list.
   * </ul>
   */
  static List<Marker> deleted(Unit unit, Units after, Map<String, String> earlierToLater) {
    var struck = after.struck().get(unit.eId());
    if (struck != null) {
      return struck;
    }
    if (unit.parent() == null) {
      return List.of();
    }
    for (var at = earlierToLater.get(unit.parent()); at != null; ) {
      var enclosing = after.get(at);
      if (enclosing == null) {
        break;
      }
      var found = new ArrayList<Marker>();
      for (var child : enclosing.element().children()) {
        var act = child.attribute("amendingAct");
        var action = child.attribute("action");
        if (child.name().equals("provenance") && act != null && "replaced".equals(action)) {
          found.add(new Marker(act, "replaced", enclosing.eId()));
        }
      }
      if (!found.isEmpty()) {
        return List.copyOf(found);
      }
      at = enclosing.parent();
    }
    return List.of();
  }

  private static void collect(Element element, Set<Marker> found) {
    for (var child : element.children()) {
      if (Units.eId(child) != null) {
        continue;
      }
      var act = child.attribute("amendingAct");
      var action = child.attribute("action");
      if (act != null && action != null) {
        found.add(new Marker(act, action, null));
      }
      collect(child, found);
    }
  }
}
