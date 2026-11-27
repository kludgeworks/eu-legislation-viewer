// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiffWithLinearSpace;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.deltamerge.InlineDeltaMergeInfo;
import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Edits.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The feature "What counts as a difference, and how its edits show": one function for each of its
 * rules about what the comparison reads. A rule reads facts and gives one new fact. It does not
 * call another rule. {@link Wording} applies the rules about what the comparison reads. {@link
 * Differ}, {@link Comparer}, {@link Pieces}, {@link Edits} and {@link Ordering} apply the rules
 * about what a difference is and how it shows.
 */
final class WhatCountsAsADifference {
  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

  /**
   * A form blank: an ellipsis that stands alone as a word, where a form leaves room to fill in. For
   * example, "Make (trade name of manufacturer): …" and "Mass in running order: … kg". The
   * Publications Office chooses whether a consolidation prints it. An ellipsis joined to a word, as
   * in "Annex…", is not a blank.
   */
  private static final Pattern BLANK = Pattern.compile("(?<![\\p{L}\\p{N}])…(?![\\p{L}\\p{N}])");

  private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

  private static final Pattern FULL_STOP_AT_THE_END = Pattern.compile("\\.$");

  /** The elements of a kept deletion. */
  private static final Set<String> KEPT_DELETIONS = Set.of("deleted");

  private WhatCountsAsADifference() {}

  /** What the comparison reads: the wording of a block, or one token of it. */
  sealed interface Read {

    /** Returns the text. */
    String text();

    /**
     * What a block says, whole. Two blocks with the same reading do not differ.
     *
     * @param text its wording
     */
    record Block(String text) implements Read {}

    /**
     * One token of a block: a word, a run of spaces, a punctuation mark. Two tokens with the same
     * reading have no edit.
     *
     * @param text the token
     */
    record Token(String text) implements Read {}
  }

  /**
   * One difference for each occurrence. An occurrence of only the later text is inserted, and an
   * occurrence of only the earlier text is deleted: each is one difference, with the occurrences in
   * it. An occurrence of both texts is changed if its own words differ: its number, its heading,
   * its blocks that are not in an occurrence of their own, and the footnotes that it refers to. An
   * occurrence in it is a difference of its own.
   *
   * @param inTheEarlierText whether the earlier text has the occurrence
   * @param inTheLaterText whether the later text has the occurrence
   * @param ownWordsDiffer whether the own words of the occurrence differ between the two texts
   * @return the kind of the difference that the occurrence is, or empty if it is none
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "one-difference-for-each-occurrence")
  static Optional<Kind> oneDifferenceForEachOccurrence(
      boolean inTheEarlierText, boolean inTheLaterText, boolean ownWordsDiffer) {
    if (!inTheEarlierText) {
      return Optional.of(Kind.INSERTED);
    }
    if (!inTheLaterText) {
      return Optional.of(Kind.DELETED);
    }
    return ownWordsDiffer ? Optional.of(Kind.CHANGED) : Optional.empty();
  }

  /**
   * A changed occurrence and the occurrences in it that differ are one difference, shown whole, if
   * the same acts made all of them. For example, 11.1.3 with its points (a) to (d), all from
   * 2019/103.
   *
   * @param occurrence an occurrence, compared
   * @return whether the occurrence shows whole, as one difference with the occurrences in it
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "same-acts-one-difference")
  static boolean sameActsOneDifference(ComparedOccurrence occurrence) {
    var difference = occurrence.difference();
    return difference != null
        && difference.kind() == Kind.CHANGED
        && occurrence.occurrences().stream().anyMatch(ComparedOccurrence::differs)
        && madeBy(occurrence, acts(difference));
  }

  /** Returns whether {@code acts} made every difference in {@code occurrence}. */
  private static boolean madeBy(ComparedOccurrence occurrence, Set<String> acts) {
    for (var each : occurrence.occurrences()) {
      var in = each.difference();
      if ((in != null && !acts(in).equals(acts)) || !madeBy(each, acts)) {
        return false;
      }
    }
    return true;
  }

  /** Returns the acts that made {@code difference}, from its tags. */
  private static Set<String> acts(DifferenceBuilder difference) {
    var acts = new HashSet<String>();
    for (var tag : difference.tags()) {
      acts.add(tag.act());
    }
    return acts;
  }

  /**
   * The comparison does not compare a preamble that only one text has. The Publications Office
   * publishes some consolidated texts without the preamble, though no act removed it. For example,
   * the consolidations of 2015/1998 from 2017.
   *
   * @param earlierHasOne whether the earlier text has a preamble with occurrences in it
   * @param laterHasOne whether the later text has one
   * @return the text without a preamble, if only the other text has one
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "preamble-of-one-text")
  static Optional<Side> preambleOfOneText(boolean earlierHasOne, boolean laterHasOne) {
    if (earlierHasOne == laterHasOne) {
      return Optional.empty();
    }
    return Optional.of(earlierHasOne ? Side.LATER : Side.EARLIER);
  }

  /**
   * What the edits show. Text of both texts shows the words of the later text, and the words of the
   * earlier text deleted where they stood. Text of only the later text shows all its words
   * inserted. Text of only the earlier text shows all its words deleted, as the earlier text has
   * them.
   *
   * @param was the text as the earlier text has it. Null if only the later text has it.
   * @param is the text as the later text has it. Null if only the earlier text has it.
   * @return the text with its edits
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-the-edits-show")
  static Edited whatTheEditsShow(@Nullable Element was, @Nullable Element is) {
    if (was == null) {
      return Edits.all(Objects.requireNonNull(is), Edits.INSERTED);
    }
    return is == null ? Edits.all(was, Edits.DELETED) : Edits.between(was, is);
  }

  /**
   * A replaced phrase reads as one deletion and one insertion, not as a deletion and an insertion
   * for each word. An inserted phrase stays whole, though a word of it is also in the words around
   * it. The Myers diff in linear space of java-diff-utils aligns the tokens:
   *
   * <ul>
   *   <li>Its middle snakes keep an inserted phrase whole. The greedy diff splits it at a word that
   *       it repeats. For example, "in ... and in combination".
   *   <li>Its whitespace merger joins changes that only a run of spaces separates.
   * </ul>
   *
   * @param before the tokens of the earlier block, as the comparison reads them
   * @param after the tokens of the later block, as the comparison reads them
   * @return the runs of tokens that differ, in order
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "replaced-phrase")
  static List<AbstractDelta<String>> replacedPhrase(List<String> before, List<String> after) {
    return DiffRowGenerator.WHITESPACE_EQUALITIES_MERGER.apply(
        new InlineDeltaMergeInfo(
            DiffUtils.diff(before, after, new MyersDiffWithLinearSpace<String>(), null, false)
                .getDeltas(),
            before,
            after));
  }

  /**
   * Deleted text stands where it was: after the later text that pairs with the last paired text
   * before it, or first if there is none. Thus it comes before the text inserted in its place. The
   * text is the occurrences in one occurrence, or the blocks of one stretch that differs.
   *
   * @param laterOf for each earlier occurrence or block, the position among the later ones of the
   *     one that it pairs with, or -1
   * @param at the position of the deleted one among the earlier ones
   * @return the position of the later one that it stands before. The count of the later ones if it
   *     stands last.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "deleted-text-stands-where-it-was")
  static int deletedTextStandsWhereItWas(List<Integer> laterOf, int at) {
    var place = 0;
    for (var i = 0; i < at; i++) {
      if (laterOf.get(i) >= 0) {
        place = laterOf.get(i) + 1;
      }
    }
    return place;
  }

  /**
   * Footnotes belong to their occurrence. The comparison compares a footnote with the occurrence
   * that refers to it, through the reference and not by its number. Thus an occurrence whose
   * footnote has another number, with the same words, does not differ.
   *
   * @param was what each footnote says that the earlier occurrence refers to, in the order of the
   *     references
   * @param is the same for the later occurrence
   * @return whether the footnotes of the occurrence differ
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "footnotes-belong-to-their-occurrence")
  static boolean footnotesBelongToTheirOccurrence(List<String> was, List<String> is) {
    return !was.equals(is);
  }

  /**
   * A full stop after a number is no change: "11.3" and "11.3." are the same number. The
   * Publications Office sets the full stop, and no act changes it.
   *
   * @param num what the num of an occurrence says
   * @return the num as the comparison reads it
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "full-stop-after-a-number-is-no-change")
  static String fullStopAfterANumberIsNoChange(String num) {
    return FULL_STOP_AT_THE_END.matcher(num).replaceFirst("");
  }

  /**
   * Spaces are no change. A run of spaces, non-breaking spaces too, is one space as a token. The
   * spaces of a block do not count: "DOB: 30 March 1977" and "DOB:30 March 1977" are the same.
   *
   * @param read the block or the token
   * @return what it says, as the comparison reads it
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "spaces-are-no-change")
  static String spacesAreNoChange(Read read) {
    return switch (read) {
      case Read.Block block -> SPACE.matcher(block.text()).replaceAll("");
      case Read.Token token -> SPACE.matcher(token.text()).matches() ? " " : token.text();
    };
  }

  /**
   * The form of an apostrophe, a quotation mark or a form blank is no change. The Publications
   * Office chooses it when it sets a text.
   *
   * <ul>
   *   <li>A block reads with each apostrophe and quotation mark in its plain form, and without its
   *       form blanks. For example, "“group”" and "\"group\"" are the same, as are "Make: …" and
   *       "Make:".
   *   <li>A token that has a letter or a digit reads with its apostrophes in their plain form. Thus
   *       "institution's" and "institution’s" are the same word.
   *   <li>A quotation mark alone keeps its form. The plain form of an opening quotation mark and of
   *       a closing one is the same, and they would then pair one with the other.
   * </ul>
   *
   * @param read the block or the token
   * @return what it says, as the comparison reads it
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "form-of-apostrophes-and-quotes")
  static String formOfApostrophesAndQuotes(Read read) {
    return switch (read) {
      case Read.Block block -> BLANK.matcher(Wording.plain(block.text())).replaceAll("");
      case Read.Token token ->
          token.text().codePoints().anyMatch(Character::isLetterOrDigit)
              ? Wording.plain(token.text())
              : token.text();
    };
  }

  /**
   * A kept deletion is not words of the law: the words that an act deleted, which a consolidated
   * text keeps with the mark of a deletion.
   *
   * @param element an element of a text
   * @return whether the element is a kept deletion, which the comparison does not read
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "kept-deletion-is-not-words")
  static boolean keptDeletionIsNotWords(Element element) {
    return KEPT_DELETIONS.contains(element.name());
  }

  /**
   * A word has a letter or a digit. Spaces and punctuation alone are not words. A difference counts
   * only its words.
   *
   * @param token a token of a block
   * @return whether the token is a word
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-a-word-is")
  static boolean whatAWordIs(String token) {
    return LETTER_OR_DIGIT.matcher(token).find();
  }

  /**
   * The words of a figure: an image counts as one word, and each word of its caption and its
   * description counts as a word.
   *
   * @param figure the figure element
   * @return how many words the figure has
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "words-of-a-figure")
  static int wordsOfAFigure(Element figure) {
    var words = figureHasFile(figure) ? 1 : 0;
    for (var child : figure.children()) {
      for (var token : Wording.said(Wording.tokens(child))) {
        if (whatAWordIs(token)) {
          words++;
        }
      }
    }
    return words;
  }

  /** Returns whether {@code figure} references an image file via a non-empty {@code src}. */
  static boolean figureHasFile(Element figure) {
    var src = figure.attribute("src");
    return src != null && !src.isEmpty();
  }

  /**
   * A formula is one piece. The comparison reads it whole, as one token, not symbol by symbol: the
   * elements of its MathML alone do not read. Thus a changed formula shows whole as deleted, then
   * whole as inserted.
   *
   * @param math the formula
   * @return the one token that the formula is: the text of its MathML elements, joined
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "formula-is-one-piece")
  static String formulaIsOnePiece(Element math) {
    var out = new StringBuilder();
    text(math, out);
    return SPACE.matcher(out).replaceAll(" ").strip();
  }

  private static void text(Element element, StringBuilder out) {
    out.append(element.text());
    element.children().forEach(child -> text(child, out));
  }

  /**
   * An image is one piece. The comparison reads it as one token, its file, not by anything in it.
   * Thus a changed inline image shows whole as deleted, then whole as inserted.
   *
   * @param image the image
   * @return the one token that the image is: {@code [image <src>]}
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "image-is-one-piece")
  static String imageIsOnePiece(Element image) {
    return "[image " + image.attribute("src") + "]";
  }

  /** How two elements compare under the image difference rule. */
  enum FigureComparison {
    /** The image file changed: show the old figure as deleted and the new figure as inserted. */
    BEFORE_AND_AFTER,
    /** The image file stayed the same: keep the figure and compare its pieces. */
    BY_PIECES,
    /** One of the two elements is not a figure: this rule does not apply. */
    NOT_FIGURES
  }

  /**
   * An image shows before and after when the file of a figure changes: the comparison shows the old
   * figure as deleted and the new figure as inserted, not edits inside one figure. When two figures
   * have the same file, the comparison reads them piece by piece so caption edits show in place.
   *
   * @param was the element in the earlier text
   * @param is the element in the later text
   * @return {@link FigureComparison#BEFORE_AND_AFTER} or {@link FigureComparison#BY_PIECES} when
   *     both elements are figures, or {@link FigureComparison#NOT_FIGURES} otherwise
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "image-shows-before-and-after")
  static FigureComparison imageShowsBeforeAndAfter(Element was, Element is) {
    if (!was.name().equals("figure") || !is.name().equals("figure")) {
      return FigureComparison.NOT_FIGURES;
    }
    return Objects.equals(was.attribute("src"), is.attribute("src"))
        ? FigureComparison.BY_PIECES
        : FigureComparison.BEFORE_AND_AFTER;
  }

  /**
   * A block that holds a figure compares by its pieces. The comparison reads it piece by piece so
   * the figure stays in its place and the words around it show their edits word by word.
   *
   * @param was the element in the earlier text
   * @param is the element in the later text
   * @return whether both elements have the same name, neither is a figure, and at least one holds a
   *     figure at any depth
   */
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "block-with-a-figure-compares-by-its-pieces")
  static boolean blockWithAFigureComparesByItsPieces(Element was, Element is) {
    return !was.name().equals("figure")
        && !is.name().equals("figure")
        && was.name().equals(is.name())
        && (holdsFigure(was) || holdsFigure(is));
  }

  private static boolean holdsFigure(Element element) {
    return element.children().stream()
        .anyMatch(child -> child.name().equals("figure") || holdsFigure(child));
  }
}
