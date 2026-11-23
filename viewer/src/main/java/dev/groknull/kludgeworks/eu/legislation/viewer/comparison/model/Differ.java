// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Finds how one text differs from another, as the Differences view shows it. It works in four
 * stages:
 *
 * <ol>
 *   <li>{@link Occurrences} reads the occurrences of each text.
 *   <li>{@link Pairing} pairs the occurrences of the two texts: siblings with siblings, and an
 *       occurrence that moved into the occurrence before it, or out of it.
 *   <li>{@link Comparer} compares the occurrences, from the outermost occurrences inwards. An
 *       occurrence in only one text is inserted or deleted, with its occurrences. An occurrence in
 *       both texts whose own wording differs is changed ({@link OccurrenceEdits}), and the
 *       occurrences in it are compared in turn. Each is a {@link ComparedOccurrence}.
 *   <li>{@link Ordering} puts the differences in the order of the later text, each with what the
 *       later text has before it. {@link ShownOccurrence} gives an occurrence as its difference
 *       shows it.
 * </ol>
 *
 * <p>The occurrences of the two texts are in one order: the occurrences of the later text in their
 * order, and each deleted occurrence after the occurrence that it stood after. Thus a deleted
 * occurrence comes before the occurrences inserted in its place.
 *
 * <p>The footnotes that an occurrence refers to are compared as part of it. A changed occurrence
 * shows its footnotes under it, with their edits, if they differ. An inserted or deleted occurrence
 * shows its footnotes, whole, as insertions or deletions. A footnote that only one text has, and
 * each reference to it, says so ({@link Edits#ONLY}).
 *
 * <p>The own blocks of a changed occurrence are matched by what they say ({@link Pieces}). {@link
 * Edits} shows the edits of a changed block word by word.
 *
 * <p>Each occurrence of the later text is placed once, as one of these:
 *
 * <ul>
 *   <li>a difference
 *   <li>the opening of an occurrence that holds differences
 *   <li>an occurrence that is the same in both texts, with its occurrences. It is folded in a run
 *       of such occurrences in the same occurrence.
 * </ul>
 *
 * <p>These rules decide what counts as one difference:
 *
 * <ul>
 *   <li>A changed occurrence and the occurrences in it that differ are one difference if the same
 *       acts made all of them. The difference shows whole, and each occurrence shows its edits
 *       where it differs. For example, 11.1.3 with its points (a) to (d), all from 2019/103.
 *   <li>If the num of an occurrence only gained or lost its full stop, no act changed it. Thus the
 *       occurrence does not differ. For example, "11.3" to "11.3.".
 * </ul>
 */
public final class Differ {

  private Differ() {}

  /**
   * The differences of a later text from an earlier one, and what the later text has after the last
   * difference.
   *
   * @param differences the differences, in the order of the later text, each with what comes before
   *     it
   * @param closing what the later text has after the last difference. All of the later text if
   *     there are no differences.
   * @param withoutPreamble the text without a preamble, if the other text has one. The preamble is
   *     then not compared. Null if both texts have one, or if neither has one.
   */
  public record Compared(
      List<Difference> differences, List<Context> closing, @Nullable Side withoutPreamble) {
    public Compared {
      differences = List.copyOf(differences);
      closing = List.copyOf(closing);
    }
  }

  /**
   * Returns the differences of {@code to} from {@code from}, in the order of {@code to}. Each
   * deleted occurrence is where it stood, before the occurrences inserted in its place.
   *
   * <p>If only one text has a preamble, the preamble is not compared. The Publications Office
   * publishes some consolidations without the preamble, although no act removed it. For example,
   * the consolidations of 2015/1998 from 2017.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "preamble-of-one-text")
  public static Compared differences(Element from, Element to) {
    var earlierPreamble = preamble(from);
    var laterPreamble = preamble(to);
    @Nullable Side withoutPreamble =
        earlierPreamble == laterPreamble ? null : earlierPreamble ? Side.LATER : Side.EARLIER;
    var before = Occurrences.of(withoutPreamble == Side.LATER ? withoutPreamble(from) : from);
    var after = Occurrences.of(withoutPreamble == Side.EARLIER ? withoutPreamble(to) : to);
    var show = new Ordering();
    show.occurrences(
        new Comparer(before, after, Pairing.of(before, after)).occurrences(null, null), null);
    return new Compared(show.placed(), show.closing(), withoutPreamble);
  }

  /**
   * Returns whether {@code text} has a preamble with occurrences in it. The occurrences are its
   * citations and its recitals. Legal-text has the preamble only in the text itself, between its
   * preface and its body, so the search stops there.
   */
  private static boolean preamble(Element text) {
    if (text.name().equals("preamble")) {
      return Occurrences.holdsOccurrences(text);
    }
    for (var child : text.children()) {
      if (child.name().equals("preamble") && Occurrences.holdsOccurrences(child)) {
        return true;
      }
    }
    return false;
  }

  /** Returns {@code element} without its preamble. */
  private static Element withoutPreamble(Element element) {
    var children = new ArrayList<Element>();
    for (var child : element.children()) {
      if (!child.name().equals("preamble")) {
        children.add(withoutPreamble(child));
      }
    }
    return copy(element, children);
  }

  static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }

  /**
   * Returns {@code footnotes} as the footnotes of an occurrence, under its blocks and occurrences.
   */
  static Element footnotes(List<Element> footnotes) {
    return new Element("footnotes", Map.of(), footnotes, "");
  }
}
