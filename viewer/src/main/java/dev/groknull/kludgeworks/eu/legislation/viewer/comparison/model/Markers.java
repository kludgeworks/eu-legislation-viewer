// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds the amendment markers on the own content of an occurrence.
 *
 * <p>The converter keeps the markers of a consolidation as legal-text {@code provenance}, {@code
 * changed} and {@code deleted}. Each names its amending act and what the act did. The own markers
 * of an occurrence are those of the occurrence and its blocks, not those of the occurrences in it.
 * Each marker is returned once, in the order in which they stand.
 *
 * <p>If an occurrence has no markers of its own, it takes the provenance of the nearest enclosing
 * occurrence that has a provenance of its own. An act that replaced or inserted that occurrence
 * whole did so with the occurrences in it.
 */
final class Markers {
  private Markers() {}

  /**
   * Returns the markers on the own content of {@code occurrence}, in {@code occurrences}. If it has
   * none, returns the provenance of the nearest enclosing occurrence that has one. Those markers
   * name that enclosing occurrence in {@code in}.
   */
  static List<Marker> of(Occurrence occurrence, Occurrences occurrences) {
    var found = new LinkedHashSet<Marker>();
    collect(occurrence.element(), found);
    for (var at = occurrence.parent(); found.isEmpty() && at != null; ) {
      var enclosing = occurrences.get(at);
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
   * Returns the markers of the act that deleted {@code occurrence} of the earlier text. The later
   * text {@code after} tells which act it was. The occurrence was in the occurrence that {@code in}
   * is in the later text. {@code in} is null for an outermost occurrence.
   *
   * <ul>
   *   <li>If a deletion keeps the occurrence struck, it returns the markers of that deletion.
   *   <li>If not, it returns the provenance of the nearest enclosing occurrence that an act
   *       replaced whole, without the occurrence. Those markers name that enclosing occurrence in
   *       {@code in}.
   *   <li>If neither applies, it returns an empty list.
   * </ul>
   */
  static List<Marker> deleted(Occurrence occurrence, Occurrences after, @Nullable Occurrence in) {
    var struck = after.struck().get(occurrence.eId());
    if (struck != null) {
      return struck;
    }
    for (var enclosing = in; enclosing != null; ) {
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
      enclosing = enclosing.parent() == null ? null : after.get(enclosing.parent());
    }
    return List.of();
  }

  private static void collect(Element element, Set<Marker> found) {
    for (var child : element.children()) {
      if (Occurrences.eId(child) != null) {
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
