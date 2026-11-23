// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiffWithLinearSpace;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.deltamerge.InlineDeltaMergeInfo;
import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shows the edits of a comparison in legal-text: its insertions and deletions. Inserted words go in
 * {@code diff:ins}, and deleted words go in {@code diff:del}. The edits are only around text, a
 * formula or an image, that is the words of a {@code t}, a {@code number}, a {@code math} or an
 * {@code img}. A quotation mark of a {@code quote} that is edited becomes such text. Thus an
 * element keeps the parent that it has in the text.
 *
 * <p>A block is read as tokens ({@link Wording}). The tokens of a {@code t}, a {@code number}, a
 * formula and an image are edited, and so are the quotation marks of a quote. The others are only
 * counted. A deleted run shows its tokens as they were, a formula as a formula. {@link Runs} writes
 * the edits.
 *
 * <p>A table is not one run of tokens. It is compared by its rows and cells. See {@link #pieces}.
 *
 * <p>The Myers diff in linear space of java-diff-utils aligns the tokens of two blocks.
 *
 * <ul>
 *   <li>Its middle snakes keep an inserted phrase whole. The greedy diff splits it at a word that
 *       it repeats. For example, "in ... and in combination".
 *   <li>Its whitespace merger joins changes that only a run of spaces separates. Thus a replaced
 *       phrase reads as one deletion and one insertion.
 *   <li>A change of spaces alone is not a change.
 * </ul>
 */
final class Edits {
  static final String INSERTED = "diff:ins";
  static final String DELETED = "diff:del";

  /**
   * The attribute of a footnote, or of a reference to a footnote, that only one of the two texts
   * has. Its value is "ins" or "del". The marker of a footnote and a reference to a footnote are
   * not words, so no edit is around them. With the attribute, a page can show them only with the
   * text that has them.
   */
  static final String ONLY = "diff";

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  private Edits() {}

  /**
   * Returns {@code to}, edited against {@code from}. The words of {@code to} that {@code from} does
   * not have are shown inserted. The words of {@code from} that {@code to} does not have are added
   * as deleted. Each deleted run goes before the token of {@code to} that it stood before.
   *
   * <p>A table is compared by its pieces. See {@link #PIECES}.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-the-edits-show")
  static Edited between(Element from, Element to) {
    return from.name().equals(to.name()) && PIECES.contains(to.name())
        ? pieces(from, to)
        : words(from, to);
  }

  /**
   * The elements that a comparison reads as a sequence of pieces, not as one run of words. They are
   * a table and what is in it: its caption, its rows and groups of rows, its cells, and the lists,
   * occurrences and footnotes in a cell. Thus the words deleted from a cell stay in that cell, and
   * a row inserted or deleted whole shows as a row.
   */
  private static final Set<String> PIECES =
      Set.of(
          "table",
          "caption",
          "rowGroup",
          "tr",
          "td",
          "list",
          "occurrence",
          "footnotes",
          "footnote");

  /**
   * Returns {@code to}, edited against {@code from} piece by piece.
   *
   * <ul>
   *   <li>The pieces match by name and by what they say, and those that differ pair if they are
   *       alike. See {@link Pieces}.
   *   <li>A piece that is in both texts and differs gets its edits from {@link #between}.
   *   <li>A piece that is only in {@code to} is shown inserted whole.
   *   <li>A piece that is only in {@code from} is shown deleted whole, where it stood.
   * </ul>
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "table-row-by-row")
  private static Edited pieces(Element from, Element to) {
    var before = from.children();
    var after = to.children();
    var differing = Pieces.differing(before, after, keys(before), keys(after));
    var shown = new Pieces.Shown();
    for (var each : differing) {
      switch (each) {
        case Pieces.Changed(int at, Element was, Element is) -> {
          if (was.name().equals(is.name())) {
            shown.replace(at, between(was, is));
          } else {
            shown.delete(at, all(was, DELETED));
            shown.replace(at, all(is, INSERTED));
          }
        }
        case Pieces.Inserted(int at, Element is) -> shown.replace(at, all(is, INSERTED));
        case Pieces.Deleted(int at, Element was) -> {
          if (!Wording.SILENT.contains(was.name())) {
            shown.delete(at, all(was, DELETED));
          }
        }
      }
    }
    return new Edited(Differ.copy(to, shown.of(after)), shown.inserted(), shown.deleted());
  }

  /** Returns what each of {@code pieces} is matched on: its name and its {@link Wording#key}. */
  private static List<String> keys(List<Element> pieces) {
    return pieces.stream().map(piece -> piece.name() + ":" + Wording.key(piece)).toList();
  }

  /** Returns {@code to}, edited against {@code from} word by word. */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "replaced-phrase")
  private static Edited words(Element from, Element to) {
    var before = new ArrayList<String>();
    var pieces = new ArrayList<Element>();
    Wording.collect(from, before, pieces);
    var after = Wording.tokens(to);
    var beforeSaid = Wording.said(before);
    var afterSaid = Wording.said(after);
    var edited = new boolean[after.size()];
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var deltas =
        DiffRowGenerator.WHITESPACE_EQUALITIES_MERGER.apply(
            new InlineDeltaMergeInfo(
                DiffUtils.diff(
                        beforeSaid, afterSaid, new MyersDiffWithLinearSpace<String>(), null, false)
                    .getDeltas(),
                beforeSaid,
                afterSaid));
    for (var delta : deltas) {
      var source = delta.getSource();
      var target = delta.getTarget();
      if (spaces(source.getLines()) && spaces(target.getLines())) {
        continue;
      }
      for (var i = 0; i < target.size(); i++) {
        edited[target.getPosition() + i] = true;
      }
      inserted += words(target.getLines());
      if (source.size() > 0) {
        deletedAt
            .computeIfAbsent(target.getPosition(), at -> new ArrayList<>())
            .addAll(pieces.subList(source.getPosition(), source.getPosition() + source.size()));
        deleted += words(source.getLines());
      }
    }
    return new Edited(Runs.edited(to, INSERTED, edited, deletedAt), inserted, deleted);
  }

  /**
   * Returns {@code element} with every word in it edited {@code edit}, which is {@link #INSERTED}
   * or {@link #DELETED}.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-the-edits-show")
  static Edited all(Element element, String edit) {
    var tokens = Wording.tokens(element);
    var edited = new boolean[tokens.size()];
    Arrays.fill(edited, true);
    var words = words(Wording.said(tokens));
    var shown =
        only(Runs.edited(element, edit, edited, Map.of()), edit.equals(INSERTED) ? "ins" : "del");
    return edit.equals(INSERTED) ? new Edited(shown, words, 0) : new Edited(shown, 0, words);
  }

  /**
   * Returns {@code element} with each footnote and each reference to a footnote in it set {@link
   * #ONLY}.
   */
  private static Element only(Element element, String value) {
    if (element.name().equals("footnote") || element.name().equals("footnoteRef")) {
      var attributes = new HashMap<>(element.attributes());
      attributes.put(ONLY, value);
      return new Element(element.name(), attributes, element.children(), element.text());
    }
    return Differ.copy(
        element, element.children().stream().map(child -> only(child, value)).toList());
  }

  /**
   * Returns {@code element} with each reference to an inserted footnote of it set {@link #ONLY}. A
   * block that changed can refer to a footnote that only the later text has. The reference is then
   * only in the later text also.
   */
  static Element referring(Element element) {
    var inserted = new HashSet<String>();
    insertedFootnotes(element, inserted);
    return inserted.isEmpty() ? element : referring(element, inserted);
  }

  private static void insertedFootnotes(Element element, Set<String> found) {
    if (element.name().equals("footnote")) {
      var eId = element.attribute("eId");
      if (eId != null && "ins".equals(element.attribute(ONLY))) {
        found.add("#" + eId);
      }
      return;
    }
    element.children().forEach(child -> insertedFootnotes(child, found));
  }

  private static Element referring(Element element, Set<String> inserted) {
    if (element.name().equals("footnoteRef")) {
      return element.attribute(ONLY) == null && inserted.contains(element.attribute("href"))
          ? only(element, "ins")
          : element;
    }
    return Differ.copy(
        element, element.children().stream().map(child -> referring(child, inserted)).toList());
  }

  /**
   * An element as a comparison shows it, edited.
   *
   * @param element the element, its words edited
   * @param inserted how many words in it are inserted
   * @param deleted how many words in it are deleted
   */
  record Edited(Element element, int inserted, int deleted) {}

  private static boolean spaces(List<String> tokens) {
    return tokens.stream().allMatch(String::isBlank);
  }

  /**
   * Returns how many of {@code tokens} are words. A word has a letter or a digit. Spaces and
   * punctuation alone are not words.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-a-word-is")
  private static int words(List<String> tokens) {
    return (int) tokens.stream().filter(token -> WORD.matcher(token).find()).count();
  }
}
