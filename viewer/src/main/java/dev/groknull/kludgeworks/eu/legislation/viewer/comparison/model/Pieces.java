// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Edits.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.WhatCountsAsADifference.Stood;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pieces of one element against the pieces of the same element in the other text. The pieces
 * are the own blocks of an occurrence, or the rows and cells of a table.
 *
 * <p>The pieces that say the same in both texts match, in order, with the Myers diff of
 * java-diff-utils. Between two matches, each piece differs:
 *
 * <ul>
 *   <li>Pieces that are alike pair: the piece changed. See {@link
 *       HowTwoTextsPair#blocksPairWithWhatTheyWere}.
 *   <li>A piece of only the later text is inserted.
 *   <li>A piece of only the earlier text is deleted. It stood before the first later piece that
 *       pairs with a piece after it, as the rule deleted-text-stands-where-it-was tells.
 * </ul>
 */
final class Pieces {
  private Pieces() {}

  /** A piece that differs. */
  sealed interface Differing {
    /**
     * Returns the position of the piece among the later pieces. For a deleted piece, it is the
     * position of the later piece that it stood before, or the count of later pieces if it stood
     * after the last.
     */
    int at();
  }

  /** A piece in both texts: {@code was} in the earlier text, {@code is} in the later. */
  record Changed(int at, Element was, Element is) implements Differing {}

  /** The piece {@code is} of only the later text. */
  record Inserted(int at, Element is) implements Differing {}

  /** The piece {@code was} of only the earlier text. */
  record Deleted(int at, Element was) implements Differing {}

  /**
   * Returns the pieces that differ between {@code from} and {@code to}. The pieces match on {@code
   * fromKeys} and {@code toKeys}, one key for each piece. Within one stretch of pieces that differ,
   * the later pieces come first, in order, then the deleted pieces, in order.
   */
  static List<Differing> differing(
      List<Element> from, List<Element> to, List<String> fromKeys, List<String> toKeys) {
    var found = new ArrayList<Differing>();
    for (var delta : DiffUtils.diff(fromKeys, toKeys).getDeltas()) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var earlier = from.subList(source.getPosition(), source.getPosition() + source.size());
      var later = to.subList(target.getPosition(), target.getPosition() + target.size());
      var sourceOf = HowTwoTextsPair.blocksPairWithWhatTheyWere(earlier, later);
      var earlierOf = Arrays.stream(sourceOf).boxed().toList();
      var paired = new boolean[earlier.size()];
      for (var i = 0; i < later.size(); i++) {
        var at = target.getPosition() + i;
        if (sourceOf[i] < 0) {
          found.add(new Inserted(at, later.get(i)));
        } else {
          paired[sourceOf[i]] = true;
          found.add(new Changed(at, earlier.get(sourceOf[i]), later.get(i)));
        }
      }
      for (var i = 0; i < earlier.size(); i++) {
        if (!paired[i]) {
          var stands =
              WhatCountsAsADifference.deletedTextStandsWhereItWas(new Stood.Block(earlierOf, i));
          found.add(new Deleted(target.getPosition() + stands, earlier.get(i)));
        }
      }
    }
    return found;
  }

  /**
   * The pieces of the later text as a comparison shows them: each piece that differs, with its
   * edits, and the deleted pieces where they stood.
   */
  static final class Shown {
    private final Map<Integer, Element> replaced = new HashMap<>();
    private final Map<Integer, List<Element>> deletedAt = new HashMap<>();
    private int inserted;
    private int deleted;

    /** Shows {@code edited} in the place of the later piece at the position {@code at}. */
    void replace(int at, Edited edited) {
      replaced.put(at, edited.element());
      count(edited);
    }

    /** Shows {@code edited}, a deleted piece, before the later piece at the position {@code at}. */
    void delete(int at, Edited edited) {
      deletedAt.computeIfAbsent(at, each -> new ArrayList<>()).add(edited.element());
      count(edited);
    }

    /** Counts the edited words of {@code edited}, which shows somewhere else. */
    void count(Edited edited) {
      inserted += edited.inserted();
      deleted += edited.deleted();
    }

    /**
     * Returns the later piece at the position {@code at} as it shows: with its edits, or as it is.
     */
    Element at(int at, Element piece) {
      return replaced.getOrDefault(at, piece);
    }

    /** Returns the deleted pieces that stood before the later piece at the position {@code at}. */
    List<Element> before(int at) {
      return deletedAt.getOrDefault(at, List.of());
    }

    /** Returns the later {@code pieces} as they show, with the deleted pieces where they stood. */
    List<Element> of(List<Element> pieces) {
      var shown = new ArrayList<Element>();
      for (var i = 0; i < pieces.size(); i++) {
        shown.addAll(before(i));
        shown.add(at(i, pieces.get(i)));
      }
      shown.addAll(before(pieces.size()));
      return shown;
    }

    /** Returns how many words are inserted. */
    int inserted() {
      return inserted;
    }

    /** Returns how many words are deleted. */
    int deleted() {
      return deleted;
    }
  }
}
