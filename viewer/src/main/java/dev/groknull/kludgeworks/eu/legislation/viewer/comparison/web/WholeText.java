// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
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
 *   <li>The later text does not have a deleted provision. The deleted provision stands where the
 *       comparison places it ({@link Difference#stands}): where it stood in the earlier text.
 * </ul>
 */
final class WholeText {
  private WholeText() {}

  /**
   * Returns the later text {@code later}, with the differences {@code shown} and their edits, by
   * {@code edited}. Each difference's occurrence is as {@code occurrence} gives it, for example
   * with its rows folded. A deleted difference stands where the comparison places it. One that
   * stands at the end of the text, or beside an occurrence that {@code later} does not have, goes
   * at the end.
   */
  static Content of(
      Element later,
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
      var stands = difference.stands();
      if (stands == null || !occurrences.contains(stands.eId())) {
        last.add(each);
      } else {
        (stands.after() ? after : before)
            .computeIfAbsent(stands.eId(), eId -> new ArrayList<>())
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
    return edited.apply(deleted, html.deleted().asIs(occurrence.apply(deleted)));
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
}
