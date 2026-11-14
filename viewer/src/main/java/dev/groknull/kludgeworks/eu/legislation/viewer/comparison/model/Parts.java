// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Marks.Marked;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of one element against the parts of the same element in the other text. The parts are
 * the own blocks of a unit, or the rows and cells of a table.
 *
 * <p>The parts that say the same in both texts match, in order, with the Myers diff of
 * java-diff-utils. Between two matches, each part differs:
 *
 * <ul>
 *   <li>Parts that are alike pair: the part changed. See {@link Likeness#pairs}.
 *   <li>A part of only the later text is inserted.
 *   <li>A part of only the earlier text is deleted. It stood before the first later part that pairs
 *       with a part after it.
 * </ul>
 */
final class Parts {
  private Parts() {}

  /** A part that differs. */
  sealed interface Differing {
    /**
     * Returns the position of the part among the later parts. For a deleted part, it is the
     * position of the later part that it stood before, or the count of later parts if it stood
     * after the last.
     */
    int at();
  }

  /** A part in both texts: {@code was} in the earlier text, {@code is} in the later. */
  record Changed(int at, Element was, Element is) implements Differing {}

  /** The part {@code is} of only the later text. */
  record Inserted(int at, Element is) implements Differing {}

  /** The part {@code was} of only the earlier text. */
  record Deleted(int at, Element was) implements Differing {}

  /**
   * Returns the parts that differ between {@code from} and {@code to}. The parts match on {@code
   * fromKeys} and {@code toKeys}, one key for each part. Within one stretch of parts that differ,
   * the later parts come first, in order, then the deleted parts, in order.
   */
  static List<Differing> differing(
      List<Element> from, List<Element> to, List<String> fromKeys, List<String> toKeys) {
    var found = new ArrayList<Differing>();
    for (var delta : DiffUtils.diff(fromKeys, toKeys).getDeltas()) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var earlier = from.subList(source.getPosition(), source.getPosition() + source.size());
      var later = to.subList(target.getPosition(), target.getPosition() + target.size());
      var sourceOf = Likeness.pairs(earlier, later);
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
          found.add(
              new Deleted(target.getPosition() + Likeness.before(sourceOf, i), earlier.get(i)));
        }
      }
    }
    return found;
  }

  /**
   * Returns the parts that differ between two lists of as many parts, each part with the part in
   * the same position. For example, the cells of a row with the cells of the row that it was.
   */
  static List<Differing> inOrder(
      List<Element> from, List<Element> to, List<String> fromKeys, List<String> toKeys) {
    var found = new ArrayList<Differing>();
    for (var i = 0; i < to.size(); i++) {
      if (!fromKeys.get(i).equals(toKeys.get(i))) {
        found.add(new Changed(i, from.get(i), to.get(i)));
      }
    }
    return found;
  }

  /**
   * The parts of the later text as a comparison shows them: each part that differs, marked, and the
   * deleted parts where they stood.
   */
  static final class Shown {
    private final Map<Integer, Element> replaced = new HashMap<>();
    private final Map<Integer, List<Element>> deletedAt = new HashMap<>();
    private int inserted;
    private int deleted;

    /** Shows {@code marked} in the place of the later part at the position {@code at}. */
    void replace(int at, Marked marked) {
      replaced.put(at, marked.element());
      count(marked);
    }

    /** Shows {@code marked}, a deleted part, before the later part at the position {@code at}. */
    void delete(int at, Marked marked) {
      deletedAt.computeIfAbsent(at, each -> new ArrayList<>()).add(marked.element());
      count(marked);
    }

    /** Counts the marked words of {@code marked}, which shows somewhere else. */
    void count(Marked marked) {
      inserted += marked.inserted();
      deleted += marked.deleted();
    }

    /** Returns the later part at the position {@code at} as it shows: marked, or as it is. */
    Element at(int at, Element part) {
      return replaced.getOrDefault(at, part);
    }

    /** Returns the deleted parts that stood before the later part at the position {@code at}. */
    List<Element> before(int at) {
      return deletedAt.getOrDefault(at, List.of());
    }

    /** Returns the later {@code parts} as they show, with the deleted parts where they stood. */
    List<Element> of(List<Element> parts) {
      var shown = new ArrayList<Element>();
      for (var i = 0; i < parts.size(); i++) {
        shown.addAll(before(i));
        shown.add(at(i, parts.get(i)));
      }
      shown.addAll(before(parts.size()));
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
