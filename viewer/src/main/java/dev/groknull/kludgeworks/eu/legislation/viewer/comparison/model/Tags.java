// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhoMadeAChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds the tags on the own content of an occurrence.
 *
 * <p>The converter keeps the tags of a consolidation as legal-text {@code tag}, {@code changed} and
 * {@code deleted}. Each names its amending act and its modification. The own tags of an occurrence
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
   * Returns the tags of {@code occurrence}, in {@code occurrences} ({@link
   * WhoMadeAChange#tagsOfAnOccurrence}). The tags of an occurrence around it name that occurrence
   * in {@code in}.
   */
  static List<Tag> of(Occurrence occurrence, Occurrences occurrences) {
    var own = new LinkedHashSet<Tag>();
    collect(occurrence.element(), own);
    var around = new ArrayList<List<Tag>>();
    for (var at = occurrence.parent(); at != null; ) {
      var enclosing = occurrences.get(at);
      if (enclosing == null) {
        break;
      }
      around.add(tags(enclosing, false));
      at = enclosing.parent();
    }
    return WhoMadeAChange.tagsOfAnOccurrence(List.copyOf(own), around);
  }

  /**
   * Returns the tags of the act that deleted {@code occurrence} of the earlier text ({@link
   * WhoMadeAChange#whoDeletedAnOccurrence}). The later text {@code after} tells which act it was.
   * The occurrence was in the occurrence that {@code in} is in the later text. {@code in} is null
   * for an outermost occurrence. The tags of an occurrence around it name that occurrence in {@code
   * in}.
   */
  static List<Tag> deleted(Occurrence occurrence, Occurrences after, @Nullable Occurrence in) {
    var replaced = new ArrayList<List<Tag>>();
    for (var enclosing = in; enclosing != null; ) {
      replaced.add(tags(enclosing, true));
      enclosing = enclosing.parent() == null ? null : after.get(enclosing.parent());
    }
    return WhoMadeAChange.whoDeletedAnOccurrence(
        after.keptDeletions().get(occurrence.eId()), replaced);
  }

  /**
   * Returns the tags of {@code enclosing} itself, in order. They name it in {@code in}. If {@code
   * replacedOnly} is true, returns only the tags of an act that replaced it.
   */
  private static List<Tag> tags(Occurrence enclosing, boolean replacedOnly) {
    Collection<Tag> found = replacedOnly ? new ArrayList<>() : new LinkedHashSet<>();
    for (var child : enclosing.element().children()) {
      var act = child.attribute("amendingAct");
      var modification = child.attribute("modification");
      if (child.name().equals("tag")
          && act != null
          && modification != null
          && (!replacedOnly || "replaced".equals(modification))) {
        found.add(new Tag(act, Modification.of(modification), enclosing.eId()));
      }
    }
    return List.copyOf(found);
  }

  private static void collect(Element element, Set<Tag> found) {
    for (var child : element.children()) {
      if (Occurrences.eId(child) != null) {
        continue;
      }
      var act = child.attribute("amendingAct");
      var modification = child.attribute("modification");
      if (act != null && modification != null) {
        found.add(new Tag(act, Modification.of(modification), null));
      }
      collect(child, found);
    }
  }
}
