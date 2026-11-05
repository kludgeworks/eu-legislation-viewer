// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The amendment markers on a unit's own content, as the converter keeps a consolidation's
 * (legal-text provenance, changed and deleted, each naming its amending act and what it did): the
 * unit's and its blocks', not those of the units in it, each once, in the order they stand. A unit
 * with none of its own reads as the nearest unit it is in with a provenance of its own put it: an
 * act that replaced or inserted that unit whole did so with the units in it.
 */
final class Markers {
  private Markers() {}

  /**
   * The markers on {@code unit}'s own content, in {@code units}; where it has none, the provenance
   * of the nearest unit it is in that has one, as that unit's.
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
