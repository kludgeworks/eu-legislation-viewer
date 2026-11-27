// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.WhatCountsAsADifference.FigureComparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>The rule replaced-phrase aligns the tokens of two blocks.
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

  private Edits() {}

  /**
   * Returns {@code to}, edited against {@code from}. The words of {@code to} that {@code from} does
   * not have are shown inserted. The words of {@code from} that {@code to} does not have are added
   * as deleted. Each deleted run goes before the token of {@code to} that it stood before.
   *
   * <p>A table is compared by its pieces: the rule table-row-by-row.
   */
  static Edited between(Element from, Element to) {
    if (HowTwoTextsPair.tableRowByRow(from, to)
        || WhatCountsAsADifference.imageShowsBeforeAndAfter(from, to) == FigureComparison.BY_PIECES
        || WhatCountsAsADifference.blockWithAFigureComparesByItsPieces(from, to)) {
      return pieces(from, to);
    }
    return words(from, to);
  }

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
  private static Edited pieces(Element from, Element to) {
    var before = from.children();
    var after = to.children();
    var differing = Pieces.differing(before, after, keys(before), keys(after));
    var shown = new Pieces.Shown();
    for (var each : differing) {
      switch (each) {
        case Pieces.Changed(int at, Element was, Element is) -> {
          if (was.name().equals(is.name())
              && WhatCountsAsADifference.imageShowsBeforeAndAfter(was, is)
                  != FigureComparison.BEFORE_AND_AFTER) {
            shown.replace(at, WhatCountsAsADifference.whatTheEditsShow(was, is));
          } else {
            shown.delete(at, WhatCountsAsADifference.whatTheEditsShow(was, null));
            shown.replace(at, WhatCountsAsADifference.whatTheEditsShow(null, is));
          }
        }
        case Pieces.Inserted(int at, Element is) ->
            shown.replace(at, WhatCountsAsADifference.whatTheEditsShow(null, is));
        case Pieces.Deleted(int at, Element was) -> {
          if (!WhatCountsAsADifference.keptDeletionIsNotWords(was)) {
            shown.delete(at, WhatCountsAsADifference.whatTheEditsShow(was, null));
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

  /**
   * Returns {@code to}, edited against {@code from} word by word. The rule replaced-phrase aligns
   * the tokens. A change of spaces alone is not a change.
   */
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
    var deltas = WhatCountsAsADifference.replacedPhrase(beforeSaid, afterSaid);
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
  static Edited all(Element element, String edit) {
    var tokens = Wording.tokens(element);
    var words = words(Wording.said(tokens));
    var marker = edit.equals(INSERTED) ? "ins" : "del";
    if (element.name().equals("figure")) {
      var wrapped = Runs.wrap(edit, only(element, marker));
      return edit.equals(INSERTED) ? new Edited(wrapped, words, 0) : new Edited(wrapped, 0, words);
    }
    var edited = new boolean[tokens.size()];
    Arrays.fill(edited, true);
    var shown = only(Runs.edited(element, edit, edited, Map.of()), marker);
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

  /** Returns how many of {@code tokens} are words. */
  private static int words(List<String> tokens) {
    return (int) tokens.stream().filter(WhatCountsAsADifference::whatAWordIs).count();
  }
}
