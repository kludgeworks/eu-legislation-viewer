// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer;
import gg.jte.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Renders the Whole text view of a comparison. It shows every provision of the later text as it
 * reads. Each shown difference shows its edits in place, as {@code edited} renders it. For example,
 * with its number and its sidenote.
 *
 * <ul>
 *   <li>A changed provision has its own content with its edits, then the occurrences in it. These
 *       occurrences can also differ.
 *   <li>An inserted provision is one insertion, as a whole.
 *   <li>The later text does not have a deleted provision. The deleted provision stands where it
 *       stood in the earlier text. It goes after the occurrence before it there. If there is none,
 *       it goes before the occurrence after it. If there is none, it goes at the end.
 * </ul>
 *
 * <p>The differences that are not shown read as they are. For example, the differences of other
 * acts when an act filter is on.
 */
final class WholeText {
  private WholeText() {}

  /**
   * Returns the later text {@code later}, with the differences {@code shown} and their edits, by
   * {@code edited}. Each difference's occurrence is as {@code occurrence} gives it, for example
   * with its rows folded. It places the deleted differences by the occurrences of the earlier text.
   * {@code earlier} has the eIds of these occurrences in document order.
   */
  static Content of(
      Element later,
      List<String> earlier,
      List<DifferencesView.Shown> shown,
      LegalRenderer html,
      Function<DifferencesView.Shown, Element> occurrence,
      BiFunction<DifferencesView.Shown, Content, Content> edited) {
    var occurrences = new HashSet<String>();
    collect(later, occurrences);
    var at = new HashMap<String, DifferencesView.Shown>();
    var before = new HashMap<String, List<DifferencesView.Shown>>();
    var after = new HashMap<String, List<DifferencesView.Shown>>();
    var last = new ArrayList<DifferencesView.Shown>();
    for (var each : shown) {
      var difference = each.difference();
      if (difference.kind() != Kind.DELETED) {
        at.put(difference.eId(), each);
        continue;
      }
      var placed = place(difference.eId(), difference.parent(), earlier, occurrences);
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
          (element, hooked) ->
              out -> {
                for (var deleted : before.getOrDefault(eId, List.of())) {
                  deletion(deleted, hooked, occurrence, edited).writeTo(out);
                }
                var difference = at.get(eId);
                if (difference == null) {
                  hooked.asIs(element).writeTo(out);
                } else if (difference.difference().kind() == Kind.CHANGED) {
                  // Edits its own content, then write the occurrences in it as the text has them.
                  edited.apply(difference, hooked.asIs(occurrence.apply(difference))).writeTo(out);
                  for (var inside : inside(element)) {
                    hooked.element(inside).writeTo(out);
                  }
                } else {
                  edited.apply(difference, hooked.asIs(occurrence.apply(difference))).writeTo(out);
                }
                for (var deleted : after.getOrDefault(eId, List.of())) {
                  deletion(deleted, hooked, occurrence, edited).writeTo(out);
                }
              });
    }
    var hooked = html.instead(instead);
    return out -> {
      hooked.element(later).writeTo(out);
      for (var deleted : last) {
        deletion(deleted, hooked, occurrence, edited).writeTo(out);
      }
    };
  }

  private static Content deletion(
      DifferencesView.Shown deleted,
      LegalRenderer html,
      Function<DifferencesView.Shown, Element> occurrence,
      BiFunction<DifferencesView.Shown, Content, Content> edited) {
    return edited.apply(deleted, html.asIs(occurrence.apply(deleted)));
  }

  /**
   * Returns where a deleted occurrence {@code eId} in {@code parent} stands among the {@code
   * occurrences} of the later text.
   *
   * <ul>
   *   <li>It stands after the nearest occurrence before it in the earlier text that the later text
   *       has. If that occurrence is inside a sibling, it stands after that sibling.
   *   <li>If that occurrence is its parent, or there is no such occurrence, it stands before the
   *       nearest occurrence after it that the later text has. The occurrences in it do not count.
   *       If that occurrence is inside a sibling, it stands before that sibling.
   *   <li>If there is no occurrence after it either, returns none.
   * </ul>
   *
   * <p>Also returns none if the earlier text does not have {@code eId}.
   */
  private static @Nullable Placed place(
      String eId, @Nullable String parent, List<String> earlier, Set<String> occurrences) {
    var index = earlier.indexOf(eId);
    if (index < 0) {
      return null;
    }
    for (var i = index - 1; i >= 0; i--) {
      var each = earlier.get(i);
      if (!occurrences.contains(each)) {
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
      if (each.startsWith(eId + "/") || !occurrences.contains(each)) {
        continue;
      }
      var beside = beside(each, parent);
      return new Placed(beside == null ? each : beside, false);
    }
    return null;
  }

  /**
   * Returns the occurrence that contains {@code eId} and is a child of {@code parent}. If {@code
   * eId} is itself a child of {@code parent}, returns {@code eId}. Returns none if {@code eId} is
   * {@code parent}, or if it is not inside {@code parent}.
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
   * Returns the outermost occurrences in {@code occurrence}, in document order. These are the
   * occurrences in it that are not inside another occurrence in it.
   */
  private static List<Element> inside(Element occurrence) {
    var occurrences = new ArrayList<Element>();
    for (var child : occurrence.children()) {
      outermost(child, occurrences);
    }
    return occurrences;
  }

  private static void outermost(Element element, List<Element> occurrences) {
    if (eId(element) != null) {
      occurrences.add(element);
      return;
    }
    for (var child : element.children()) {
      outermost(child, occurrences);
    }
  }

  private static void collect(Element element, Set<String> occurrences) {
    var eId = eId(element);
    if (eId != null) {
      occurrences.add(eId);
    }
    for (var child : element.children()) {
      collect(child, occurrences);
    }
  }

  private static @Nullable String eId(Element element) {
    return element.name().equals("occurrence") ? element.attribute("eId") : null;
  }

  /**
   * Where a deleted occurrence stands.
   *
   * @param eId the occurrence of the later text that it stands beside
   * @param after whether it stands after that occurrence, or before it
   */
  private record Placed(String eId, boolean after) {}
}
