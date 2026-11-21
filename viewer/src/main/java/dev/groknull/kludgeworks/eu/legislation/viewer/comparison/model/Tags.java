// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds the tags on the own content of an occurrence.
 *
 * <p>The converter keeps the tags of a consolidation as legal-text {@code tag}, {@code changed} and
 * {@code deleted}. Each names its amending act and what the act did. The own tags of an occurrence
 * are those of the occurrence and its blocks, not those of the occurrences in it. Each tag is
 * returned once, in the order in which they stand.
 *
 * <p>If an occurrence has no tags of its own, it takes the tags of the nearest enclosing occurrence
 * that has tags of its own. An act that replaced or inserted that occurrence whole did so with the
 * occurrences in it.
 */
final class Tags {
  private Tags() {}

  /**
   * Returns the tags on the own content of {@code occurrence}, in {@code occurrences}. If it has
   * none, returns the tags of the nearest enclosing occurrence that has some. Those tags name that
   * enclosing occurrence in {@code in}.
   */
  static List<Tag> of(Occurrence occurrence, Occurrences occurrences) {
    var found = new LinkedHashSet<Tag>();
    collect(occurrence.element(), found);
    for (var at = occurrence.parent(); found.isEmpty() && at != null; ) {
      var enclosing = occurrences.get(at);
      if (enclosing == null) {
        break;
      }
      for (var child : enclosing.element().children()) {
        var act = child.attribute("amendingAct");
        var action = child.attribute("action");
        if (child.name().equals("tag") && act != null && action != null) {
          found.add(new Tag(act, action, enclosing.eId()));
        }
      }
      at = enclosing.parent();
    }
    return List.copyOf(found);
  }

  /**
   * Returns the tags of the act that deleted {@code occurrence} of the earlier text. The later text
   * {@code after} tells which act it was. The occurrence was in the occurrence that {@code in} is
   * in the later text. {@code in} is null for an outermost occurrence.
   *
   * <ul>
   *   <li>If a deletion keeps the occurrence struck, it returns the tags of that deletion.
   *   <li>If not, it returns the tags of the nearest enclosing occurrence that an act replaced
   *       whole, without the occurrence. Those tags name that enclosing occurrence in {@code in}.
   *   <li>If neither applies, it returns an empty list.
   * </ul>
   */
  static List<Tag> deleted(Occurrence occurrence, Occurrences after, @Nullable Occurrence in) {
    var struck = after.struck().get(occurrence.eId());
    if (struck != null) {
      return struck;
    }
    for (var enclosing = in; enclosing != null; ) {
      var found = new ArrayList<Tag>();
      for (var child : enclosing.element().children()) {
        var act = child.attribute("amendingAct");
        var action = child.attribute("action");
        if (child.name().equals("tag") && act != null && "replaced".equals(action)) {
          found.add(new Tag(act, "replaced", enclosing.eId()));
        }
      }
      if (!found.isEmpty()) {
        return List.copyOf(found);
      }
      enclosing = enclosing.parent() == null ? null : after.get(enclosing.parent());
    }
    return List.of();
  }

  private static void collect(Element element, Set<Tag> found) {
    for (var child : element.children()) {
      if (Occurrences.eId(child) != null) {
        continue;
      }
      var act = child.attribute("amendingAct");
      var action = child.attribute("action");
      if (act != null && action != null) {
        found.add(new Tag(act, action, null));
      }
      collect(child, found);
    }
  }
}
