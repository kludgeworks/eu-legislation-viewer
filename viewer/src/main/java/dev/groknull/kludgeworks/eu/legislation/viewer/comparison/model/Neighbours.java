// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the neighbours of the differences of a comparison, as the Synopsis shows them. A neighbour
 * is an occurrence near a difference that is the same in both texts.
 *
 * <p>Each neighbour is the occurrence as the later text has it, with its tags. Its tags are found
 * as for a difference ({@link Tags#of}): the tags on its own content, or else the tags of the
 * nearest enclosing occurrence that has tags of its own.
 */
public final class Neighbours {
  private Neighbours() {}

  /**
   * An occurrence the same in both texts.
   *
   * @param occurrence the occurrence, as the later text has it, with the occurrences in it
   * @param tags the tags on its own content, or the tags of the nearest enclosing occurrence
   */
  public record Neighbour(Element occurrence, List<Tag> tags) {
    public Neighbour {
      tags = List.copyOf(tags);
    }
  }

  /** Returns {@code occurrence} as a row cites it: its eId, kind and num. */
  public static Sibling sibling(Element occurrence) {
    var eId = occurrence.attribute("eId");
    var kind = occurrence.attribute("kind");
    var num = new StringBuilder();
    for (var child : occurrence.children()) {
      if (child.name().equals("num")) {
        said(child, num);
      }
    }
    return new Sibling(eId == null ? "" : eId, kind == null ? "" : kind, num.toString().strip());
  }

  private static void said(Element element, StringBuilder out) {
    var words = element.attribute("v");
    if (element.name().equals("t") && words != null) {
      out.append(words);
    }
    element.children().forEach(child -> said(child, out));
  }

  /**
   * Returns the occurrences {@code eIds} of {@code later}, by eId. An occurrence that {@code later}
   * does not have is not in the result.
   */
  @BusinessRule(group = RuleGroup.NEIGHBOURS, name = "neighbour-tags")
  public static Map<String, Neighbour> of(Element later, Set<String> eIds) {
    var occurrences = Occurrences.of(later);
    var found = new HashMap<String, Neighbour>();
    for (var eId : eIds) {
      var occurrence = occurrences.get(eId);
      if (occurrence != null) {
        found.put(eId, new Neighbour(occurrence.element(), Tags.of(occurrence, occurrences)));
      }
    }
    return found;
  }
}
