// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer;
import gg.jte.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A comparison's Whole text (Design: Mode: Compare, Whole text): the later text, every provision as
 * it reads, those shown differing marked in place, each as {@code marked} renders it (numbered,
 * with its sidenote). A changed one is its own content marked, then the units in it, which may
 * differ too; an inserted one is marked whole; a deleted one, which the later text hasn't, stands
 * where it stood in the earlier text: after the unit beside it before it there, else before the one
 * after it, else at the end. The differences not shown (an act filter's others) read as they are.
 */
final class WholeText {
  private WholeText() {}

  /**
   * The later text {@code later}, with the differences {@code shown} marked by {@code marked}, its
   * deleted ones placed by the earlier text's units ({@code earlier}, its eIds in document order).
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
                  // its own content marked, then the units in it, as the text has them
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
   * Where a deleted unit {@code eId} in {@code parent} stands among the later text's {@code units}:
   * after the unit beside it before it in the earlier text, the nearest that the later text has;
   * else, first in its parent or with none before it, before the one after it; none if there is
   * none either.
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
        // its parent: it was first in it
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
   * The unit {@code eId} is in that is beside the units in {@code parent} (itself, if it is one);
   * none if it is {@code parent} or in none of them.
   */
  private static @Nullable String beside(String eId, @Nullable String parent) {
    var prefix = parent == null ? "" : parent + "/";
    if (!eId.startsWith(prefix) || eId.equals(parent)) {
      return null;
    }
    var slash = eId.indexOf('/', prefix.length());
    return slash < 0 ? eId : eId.substring(0, slash);
  }

  /** The units in {@code unit}, outermost first, in order: in it, not in another of them. */
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
   * @param eId the later text's unit it is beside
   * @param after whether it is after that unit, or before it
   */
  private record Placed(String eId, boolean after) {}
}
