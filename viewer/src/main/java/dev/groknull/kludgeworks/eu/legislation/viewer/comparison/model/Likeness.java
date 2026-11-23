// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.text.BreakIterator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How alike two pieces of a text are, by the words that they share. A comparison pairs the pieces
 * that differ by it.
 *
 * <p>The likeness of two pieces is the Dice coefficient of their words: twice the words that they
 * share, over the words of both. Each word counts as often as it occurs.
 */
final class Likeness {
  /**
   * The likeness from which two pieces are one piece, changed. Below it, they are a piece deleted
   * and another inserted.
   */
  static final double MINIMUM = 0.5;

  /**
   * The most pairs of pieces whose likeness {@link #pairs} works out. Above it, the pieces pair in
   * order. For example, a table of 500 rows replaced whole by a table of 500 others.
   */
  private static final long MOST = 250_000;

  private Likeness() {}

  /**
   * Pairs the pieces {@code from} of one text with the pieces {@code to} of the other. They are the
   * pieces between two pieces that are the same in both texts, so each of them differs.
   *
   * <ul>
   *   <li>First, pieces that are alike pair, in order. Of the ways to pair them, the one with the
   *       greatest likeness in all wins. For example, a row changed by one word pairs with the row
   *       that it was, not with a row inserted before it.
   *   <li>Then, between two such pairs, the pieces left with the same name pair in order. For
   *       example, the num "12." with the num "13.".
   * </ul>
   *
   * @return for each piece of {@code to}, the position in {@code from} of the piece that it pairs
   *     with, or -1 if it pairs with none
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "blocks-pair-with-what-they-were")
  static int[] pairs(List<Element> from, List<Element> to) {
    var sourceOf = new int[to.size()];
    Arrays.fill(sourceOf, -1);
    if ((long) from.size() * to.size() <= MOST) {
      alike(from, to, sourceOf);
    }
    rest(from, to, sourceOf);
    return sourceOf;
  }

  /**
   * Returns where the piece {@code source} of one text, paired with none, goes among the pieces of
   * the other: before the first piece that pairs with a piece after it.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "deleted-text-stands-where-it-was")
  static int before(int[] sourceOf, int source) {
    for (var i = 0; i < sourceOf.length; i++) {
      if (sourceOf[i] > source) {
        return i;
      }
    }
    return sourceOf.length;
  }

  /** Returns the likeness of {@code earlier} and {@code later}, from 0 to 1. */
  static double of(Map<String, Integer> earlier, Map<String, Integer> later) {
    if (earlier.isEmpty() || later.isEmpty()) {
      return 0.0;
    }
    var shared = 0;
    for (var entry : earlier.entrySet()) {
      var count = later.get(entry.getKey());
      if (count != null) {
        shared += Math.min(entry.getValue(), count);
      }
    }
    return 2.0 * shared / (total(earlier) + total(later));
  }

  /** Returns how often each word occurs in {@code text}. A word has a letter or a digit. */
  static Map<String, Integer> words(String text) {
    var counts = new HashMap<String, Integer>();
    var breaks = BreakIterator.getWordInstance(Locale.ROOT);
    breaks.setText(text);
    for (int start = breaks.first(), end = breaks.next();
        end != BreakIterator.DONE;
        start = end, end = breaks.next()) {
      var word = text.substring(start, end);
      if (Character.isLetterOrDigit(word.codePointAt(0))) {
        counts.merge(word, 1, Integer::sum);
      }
    }
    return counts;
  }

  private static int total(Map<String, Integer> counts) {
    var total = 0;
    for (var count : counts.values()) {
      total += count;
    }
    return total;
  }

  /** How alike the item {@code from} of one list and the item {@code to} of another are. */
  @FunctionalInterface
  interface Score {
    /** Returns the likeness, from 0 to 1. Below {@link #MINIMUM}, the two do not pair. */
    double of(int from, int to);
  }

  /**
   * Pairs the items of two lists that are alike, in order, with the greatest likeness in all. It
   * aligns them as the longest common subsequence does, with each pair weighed by its likeness.
   *
   * @param from how many items the one list has
   * @param to how many items the other list has
   * @return for each item of the other list, the position in the one list of the item that it pairs
   *     with, or -1 if it pairs with none
   */
  static int[] best(int from, int to, Score score) {
    var sourceOf = new int[to];
    Arrays.fill(sourceOf, -1);
    var likeness = new double[from][to];
    for (var i = 0; i < from; i++) {
      for (var j = 0; j < to; j++) {
        var each = score.of(i, j);
        likeness[i][j] = each >= MINIMUM ? each : 0.0;
      }
    }
    var best = new double[from + 1][to + 1];
    var paired = new boolean[from + 1][to + 1];
    for (var i = 1; i <= from; i++) {
      for (var j = 1; j <= to; j++) {
        var skip = Math.max(best[i - 1][j], best[i][j - 1]);
        var pair = likeness[i - 1][j - 1] > 0 ? best[i - 1][j - 1] + likeness[i - 1][j - 1] : -1;
        paired[i][j] = pair > skip;
        best[i][j] = Math.max(skip, pair);
      }
    }
    for (int i = from, j = to; i > 0 && j > 0; ) {
      if (paired[i][j]) {
        sourceOf[j - 1] = i - 1;
        i--;
        j--;
      } else if (best[i - 1][j] >= best[i][j - 1]) {
        i--;
      } else {
        j--;
      }
    }
    return sourceOf;
  }

  /** Pairs the pieces that are alike, in order, with the greatest likeness in all. */
  private static void alike(List<Element> from, List<Element> to, int[] sourceOf) {
    var earlier = from.stream().map(piece -> words(Wording.of(piece))).toList();
    var later = to.stream().map(piece -> words(Wording.of(piece))).toList();
    var found =
        best(
            from.size(),
            to.size(),
            (i, j) ->
                from.get(i).name().equals(to.get(j).name())
                    ? of(earlier.get(i), later.get(j))
                    : 0.0);
    System.arraycopy(found, 0, sourceOf, 0, found.length);
  }

  /** Pairs the pieces left between two pairs, in order, while they have the same name. */
  private static void rest(List<Element> from, List<Element> to, int[] sourceOf) {
    var source = 0;
    var target = 0;
    while (target < to.size()) {
      var next = target;
      while (next < to.size() && sourceOf[next] < 0) {
        next++;
      }
      // The pieces left are from source up to the piece that next pairs, and target up to next.
      var until = next < to.size() ? sourceOf[next] : from.size();
      for (var k = 0; source + k < until && target + k < next; k++) {
        if (from.get(source + k).name().equals(to.get(target + k).name())) {
          sourceOf[target + k] = source + k;
        }
      }
      source = until + 1;
      target = next + 1;
    }
  }
}
