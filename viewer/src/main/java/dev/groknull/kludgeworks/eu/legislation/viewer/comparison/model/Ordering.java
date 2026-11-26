// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhatThePageShows;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhatThePageShows.Placed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The compared occurrences as the differences of the later text, in order, each with what the later
 * text has before it.
 *
 * @param placed the differences, in the order of the later text
 * @param closing what the later text has after the last difference
 */
record Ordering(List<Difference> placed, List<Context> closing) {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  Ordering {
    placed = List.copyOf(placed);
    closing = List.copyOf(closing);
  }

  /**
   * Returns {@code outermost}, the outermost occurrences of the two texts, compared, as the
   * differences of the later text. The rule each-occurrence-once tells how each occurrence is
   * placed.
   */
  static Ordering of(List<ComparedOccurrence> outermost) {
    var walk = new Walk();
    walk.occurrences(outermost, null);
    return new Ordering(walk.placed, walk.context);
  }

  /** One walk through the compared occurrences, with what it placed so far. */
  private static final class Walk {
    private final List<Difference> placed = new ArrayList<>();
    private List<Context> context = new ArrayList<>();

    /**
     * Places {@code nodes}, the occurrences in the occurrence {@code in} of the later text, in
     * order. {@code in} is null for the outermost occurrences.
     */
    void occurrences(List<ComparedOccurrence> nodes, @Nullable Occurrence in) {
      // The run of occurrences that are the same in both texts, which waits to be folded.
      var same = new ArrayList<Sibling>();
      for (var node : nodes) {
        var occurrence = node.occurrence();
        var difference = node.difference();
        var placed = WhatThePageShows.eachOccurrenceOnce(difference != null, node.differs());
        if (placed == Placed.IN_A_FOLD) {
          same.add(Comparer.sibling(occurrence));
          continue;
        }
        fold(same, in);
        if (placed == Placed.OPENING || difference == null) {
          context.add(
              new Context(
                  List.of(Comparer.sibling(occurrence)), occurrence.parent(), lead(occurrence)));
          occurrences(node.occurrences(), occurrence);
        } else {
          place(node, difference, in);
        }
      }
      fold(same, in);
    }

    /**
     * Places the difference of {@code node}, then the differences in it if it does not show whole.
     */
    private void place(
        ComparedOccurrence node, DifferenceBuilder difference, @Nullable Occurrence in) {
      var whole = WhatCountsAsADifference.sameActsOneDifference(node);
      if (whole) {
        var inserted = node.inserted();
        var deleted = node.deleted();
        difference.shown(ShownOccurrence.of(node, true)).inserted(inserted).deleted(deleted);
      }
      placed.add(difference.lead(in == null ? null : lead(in)).context(context).build());
      context = new ArrayList<>();
      if (!whole) {
        occurrences(node.occurrences(), node.occurrence());
      }
    }

    /** Folds the run {@code same} into the context and empties it. */
    private void fold(List<Sibling> same, @Nullable Occurrence in) {
      if (!same.isEmpty()) {
        context.add(new Context(same, in == null ? null : in.eId(), null));
        same.clear();
      }
    }
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
