// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import gg.jte.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * Renders the Whole text view of a comparison. It shows every provision of the later text as it
 * reads. Each shown difference is marked in place, as {@code marked} renders it. For example, with
 * its number and its sidenote.
 *
 * <ul>
 *   <li>A changed provision has its own content marked, then the units in it. These units can also
 *       differ.
 *   <li>An inserted provision is marked as a whole.
 *   <li>The later text does not have a deleted provision. The deleted provision stands where it
 *       stood in the earlier text. It goes after the unit before it there. If there is none, it
 *       goes before the unit after it. If there is none, it goes at the end.
 * </ul>
 *
 * <p>The differences that are not shown read as they are. For example, the differences of other
 * acts when an act filter is on.
 */
final class WholeText {
  private WholeText() {}

  /**
   * Returns the later text {@code later}, with the differences {@code shown} marked by {@code
   * marked}. It places the deleted differences by the units of the earlier text. {@code earlier}
   * has the eIds of these units in document order.
   */
  static Content of(
      Element later,
      List<String> earlier,
      List<ChangesView.Shown> shown,
      LegalRenderer html,
      BiFunction<ChangesView.Shown, Content, Content> marked) {
    var units = new HashSet<String>();
    collect(later, units);
    var at = new HashMap<String, ChangesView.Shown>();
    var before = new HashMap<String, List<ChangesView.Shown>>();
    var after = new HashMap<String, List<ChangesView.Shown>>();
    var last = new ArrayList<ChangesView.Shown>();
    for (var each : shown) {
      var difference = each.difference();
      if (difference.kind() != Kind.DELETED) {
        at.put(difference.eId(), each);
        continue;
      }
      var placed = place(difference.eId(), difference.parent(), earlier, units);
      if (placed == null) {
        last.add(each);
      } else {
        (placed.after() ? after : before)
            .computeIfAbsent(placed.eId(), eId -> new ArrayList<>())
            .add(each);
      }
    }
    var instead = new HashMap<String, LegalRenderer.Otherwise>();
    var eIds = new HashSet<String>(at.keySet());
    eIds.addAll(before.keySet());
    eIds.addAll(after.keySet());
    for (var eId : eIds) {
      instead.put(
          eId,
          (unit, hooked) ->
              out -> {
                for (var deleted : before.getOrDefault(eId, List.of())) {
                  deletion(deleted, hooked, marked).writeTo(out);
                }
                var difference = at.get(eId);
                if (difference == null) {
                  hooked.asIs(unit).writeTo(out);
                } else if (difference.difference().kind() == Kind.CHANGED) {
                  // Mark its own content, then write the units in it as the text has them.
                  marked
                      .apply(difference, hooked.asIs(difference.difference().shown()))
                      .writeTo(out);
                  for (var inside : inside(unit)) {
                    hooked.element(inside).writeTo(out);
                  }
                } else {
                  marked
                      .apply(difference, hooked.asIs(difference.difference().shown()))
                      .writeTo(out);
                }
                for (var deleted : after.getOrDefault(eId, List.of())) {
                  deletion(deleted, hooked, marked).writeTo(out);
                }
              });
    }
    var hooked = html.instead(instead);
    return out -> {
      hooked.element(later).writeTo(out);
      for (var deleted : last) {
        deletion(deleted, hooked, marked).writeTo(out);
      }
    };
  }

  private static Content deletion(
      ChangesView.Shown deleted,
      LegalRenderer html,
      BiFunction<ChangesView.Shown, Content, Content> marked) {
    return marked.apply(deleted, html.asIs(deleted.difference().shown()));
  }

  /**
   * Returns where a deleted unit {@code eId} in {@code parent} stands among the {@code units} of
   * the later text.
   *
   * <ul>
   *   <li>It stands after the nearest unit before it in the earlier text that the later text has.
   *       If that unit is inside a sibling, it stands after that sibling.
   *   <li>If that unit is its parent, or there is no such unit, it stands before the nearest unit
   *       after it that the later text has. Its own sub-units do not count. If that unit is inside
   *       a sibling, it stands before that sibling.
   *   <li>If there is no unit after it either, returns none.
   * </ul>
   *
   * <p>Also returns none if the earlier text does not have {@code eId}.
   */
  private static @Nullable Placed place(
      String eId, @Nullable String parent, List<String> earlier, Set<String> units) {
    var index = earlier.indexOf(eId);
    if (index < 0) {
      return null;
    }
    for (var i = index - 1; i >= 0; i--) {
      var each = earlier.get(i);
      if (!units.contains(each)) {
        continue;
      }
      var beside = beside(each, parent);
      if (beside == null) {
        // This is its parent, so it was first in its parent.
        break;
      }
      return new Placed(beside, true);
    }
    for (var i = index + 1; i < earlier.size(); i++) {
      var each = earlier.get(i);
      if (each.startsWith(eId + "/") || !units.contains(each)) {
        continue;
      }
      var beside = beside(each, parent);
      return new Placed(beside == null ? each : beside, false);
    }
    return null;
  }

  /**
   * Returns the unit that contains {@code eId} and is a child of {@code parent}. If {@code eId} is
   * itself a child of {@code parent}, returns {@code eId}. Returns none if {@code eId} is {@code
   * parent}, or if it is not inside {@code parent}.
   */
  private static @Nullable String beside(String eId, @Nullable String parent) {
    var prefix = parent == null ? "" : parent + "/";
    if (!eId.startsWith(prefix) || eId.equals(parent)) {
      return null;
    }
    var slash = eId.indexOf('/', prefix.length());
    return slash < 0 ? eId : eId.substring(0, slash);
  }

  /**
   * Returns the outermost units in {@code unit}, in document order. These are the units in it that
   * are not inside another unit in it.
   */
  private static List<Element> inside(Element unit) {
    var units = new ArrayList<Element>();
    for (var child : unit.children()) {
      outermost(child, units);
    }
    return units;
  }

  private static void outermost(Element element, List<Element> units) {
    if (eId(element) != null) {
      units.add(element);
      return;
    }
    for (var child : element.children()) {
      outermost(child, units);
    }
  }

  private static void collect(Element element, Set<String> units) {
    var eId = eId(element);
    if (eId != null) {
      units.add(eId);
    }
    for (var child : element.children()) {
      collect(child, units);
    }
  }

  private static @Nullable String eId(Element element) {
    return element.name().equals("unit") ? element.attribute("eId") : null;
  }

  /**
   * Where a deleted unit stands.
   *
   * @param eId the unit of the later text that it stands beside
   * @param after whether it stands after that unit, or before it
   */
  private record Placed(String eId, boolean after) {}
}
