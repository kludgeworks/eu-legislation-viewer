// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shows the compared occurrences as the differences of the later text, in order, each with what the
 * later text has before it.
 */
final class Ordering {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private final List<Difference> placed = new ArrayList<>();
  private List<Context> context = new ArrayList<>();

  /**
   * Places {@code nodes}, the occurrences in the occurrence {@code in} of the later text, in order.
   * {@code in} is null for the outermost occurrences.
   */
  @BusinessRule(group = RuleGroup.WHAT_THE_PAGE_SHOWS, name = "each-occurrence-once")
  void occurrences(List<ComparedOccurrence> nodes, @Nullable Occurrence in) {
    // The run of occurrences that are the same in both texts, which waits to be folded.
    var same = new ArrayList<Sibling>();
    for (var node : nodes) {
      var occurrence = node.occurrence();
      var difference = node.difference();
      if (!node.differs()) {
        same.add(Comparer.sibling(occurrence));
        continue;
      }
      fold(same, in);
      if (difference == null) {
        context.add(
            new Context(
                List.of(Comparer.sibling(occurrence)), occurrence.parent(), lead(occurrence)));
        occurrences(node.occurrences(), occurrence);
        continue;
      }
      var whole = node.showsWhole();
      if (whole) {
        var inserted = node.inserted();
        var deleted = node.deleted();
        difference.shown(ShownOccurrence.of(node, true)).inserted(inserted).deleted(deleted);
      }
      placed.add(difference.lead(in == null ? null : lead(in)).context(context).build());
      context = new ArrayList<>();
      if (!whole) {
        occurrences(node.occurrences(), occurrence);
      }
    }
    fold(same, in);
  }

  /** Folds the run {@code same} into the context and empties it. */
  private void fold(List<Sibling> same, @Nullable Occurrence in) {
    if (!same.isEmpty()) {
      context.add(new Context(same, in == null ? null : in.eId(), null));
      same.clear();
    }
  }

  /** Returns the differences, in the order of the later text. */
  List<Difference> placed() {
    return List.copyOf(placed);
  }

  /** Returns what the later text has after the last difference. */
  List<Context> closing() {
    return List.copyOf(context);
  }

  /**
   * Returns the opening of {@code occurrence}: its num, heading and subheading, and its first other
   * block.
   */
  private static Element lead(Occurrence occurrence) {
    var opening = new ArrayList<Element>();
    for (var block : occurrence.blocks()) {
      opening.add(block);
      if (!TITLES.contains(block.name())) {
        break;
      }
    }
    return Differ.copy(occurrence.element(), opening);
  }
}
