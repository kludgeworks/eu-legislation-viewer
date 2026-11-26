// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The feature "How the occurrences of two texts pair": one function for each of its rules about
 * occurrences. A rule reads facts and gives one new fact: the pairs that it makes. It does not call
 * another rule: {@link Pairing} applies the rules about occurrences in order, and {@link Edits} the
 * rule about tables. {@link Pieces} applies the rule about blocks.
 */
final class HowTwoTextsPair {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private HowTwoTextsPair() {}

  /**
   * Two occurrences that are one occurrence.
   *
   * @param was the occurrence of the earlier text
   * @param is the occurrence of the later text
   */
  record Pair(Occurrence was, Occurrence is) {}

  /**
   * The occurrences directly in two occurrences that pair, or the outermost occurrences of the two
   * texts.
   *
   * <p>The eId of an occurrence starts with the eId of the occurrence that it is in. If that
   * occurrence was renumbered or moved, the occurrences in it have other eIds in the two texts.
   * Thus siblings are matched by their eId without that start.
   *
   * @param earlier the occurrences of the earlier text, in order
   * @param later the occurrences of the later text, in order
   * @param earlierIn the eId of the occurrence that {@code earlier} are in. Null for the outermost
   *     occurrences.
   * @param laterIn the eId of the occurrence that {@code later} are in. Null for the outermost
   *     occurrences.
   */
  record Siblings(
      List<Occurrence> earlier,
      List<Occurrence> later,
      @Nullable String earlierIn,
      @Nullable String laterIn) {

    /** Returns the eId of each earlier occurrence, without the eId of the occurrence it is in. */
    private List<String> earlierIds() {
      return earlier.stream().map(occurrence -> relative(occurrence.eId(), earlierIn)).toList();
    }

    /** Returns the eId of each later occurrence, without the eId of the occurrence it is in. */
    private List<String> laterIds() {
      return later.stream().map(occurrence -> relative(occurrence.eId(), laterIn)).toList();
    }

    /** Returns, for each eId of the earlier occurrences, the position of the one that has it. */
    private Map<String, Integer> positions() {
      var ids = earlierIds();
      var position = new HashMap<String, Integer>();
      for (var i = 0; i < ids.size(); i++) {
        position.put(ids.get(i), i);
      }
      return position;
    }

    /**
     * Returns, for each later occurrence, whether an act renumbered it between the two texts. It
     * then has a tag of a renumbering that the earlier occurrence of the same eId does not have.
     */
    private boolean[] newlyRenumbered() {
      var position = positions();
      var ids = laterIds();
      var renumbered = new boolean[later.size()];
      for (var j = 0; j < later.size(); j++) {
        var same = position.get(ids.get(j));
        var tags = renumberings(later.get(j));
        renumbered[j] =
            !tags.isEmpty()
                && !tags.equals(same == null ? Set.of() : renumberings(earlier.get(same)));
      }
      return renumbered;
    }
  }

  /**
   * An occurrence that an act newly renumbered pairs with the earlier occurrence that it was. That
   * occurrence has another eId, and says the same or is alike from {@link Likeness#MINIMUM},
   * without its num and its headings. Renumbering keeps the order of the occurrences, so these
   * pairs are in order, with the greatest likeness in all.
   *
   * @param siblings the occurrences in two occurrences that pair
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "renumbered-occurrence")
  static List<Pair> renumberedOccurrence(Siblings siblings) {
    var renumbered = siblings.newlyRenumbered();
    var candidates = new ArrayList<Integer>();
    for (var j = 0; j < renumbered.length; j++) {
      if (renumbered[j]) {
        candidates.add(j);
      }
    }
    if (candidates.isEmpty()) {
      return List.of();
    }
    var earlier = siblings.earlier();
    var later = siblings.later();
    var earlierIds = siblings.earlierIds();
    var laterIds = siblings.laterIds();
    var earlierSaid = earlier.stream().map(HowTwoTextsPair::substantive).toList();
    var earlierWords = earlierSaid.stream().map(Likeness::words).toList();
    var laterSaid = candidates.stream().map(j -> substantive(later.get(j))).toList();
    var laterWords = laterSaid.stream().map(Likeness::words).toList();
    var found =
        Likeness.best(
            earlier.size(),
            candidates.size(),
            (i, k) -> {
              if (earlierIds.get(i).equals(laterIds.get(candidates.get(k)))) {
                return 0.0;
              }
              var said = laterSaid.get(k);
              return !said.isEmpty() && said.equals(earlierSaid.get(i))
                  ? 1.0
                  : Likeness.of(earlierWords.get(i), laterWords.get(k));
            });
    var pairs = new ArrayList<Pair>();
    for (var k = 0; k < found.length; k++) {
      if (found[k] >= 0) {
        pairs.add(new Pair(earlier.get(found[k]), later.get(candidates.get(k))));
      }
    }
    return pairs;
  }

  /**
   * Among siblings, an occurrence pairs with the earlier sibling of the same eId: by its place in
   * the occurrence around it. This rule is not for an occurrence that an act newly renumbered, nor
   * for an earlier sibling that pairs with one.
   *
   * <ul>
   *   <li>The place of the occurrence around it does not count. Thus the occurrences in a
   *       renumbered occurrence, or in an occurrence that moved, still pair.
   *   <li>The words of the occurrence do not count. Thus an occurrence that an act replaced where
   *       it stands pairs with what it was.
   * </ul>
   *
   * @param siblings the occurrences in two occurrences that pair
   * @param renumbered the pairs of the renumbered occurrences among them
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "place-among-siblings", revision = 2)
  static List<Pair> placeAmongSiblings(Siblings siblings, List<Pair> renumbered) {
    var taken = new HashSet<String>();
    for (var pair : renumbered) {
      taken.add(pair.was().eId());
    }
    var position = siblings.positions();
    var newlyRenumbered = siblings.newlyRenumbered();
    var laterIds = siblings.laterIds();
    var pairs = new ArrayList<Pair>();
    for (var j = 0; j < siblings.later().size(); j++) {
      var same = position.get(laterIds.get(j));
      if (!newlyRenumbered[j] && same != null) {
        var was = siblings.earlier().get(same);
        if (taken.add(was.eId())) {
          pairs.add(new Pair(was, siblings.later().get(j)));
        }
      }
    }
    return pairs;
  }

  /** The occurrences that can be one occurrence, moved. */
  sealed interface Moved {

    /**
     * Occurrences that one text has in an occurrence, and the other text has right after that
     * occurrence, as its siblings. None of them pairs yet.
     *
     * @param earlier those of the earlier text, in order
     * @param later those of the later text, in order
     */
    record IntoOrOutOf(List<Occurrence> earlier, List<Occurrence> later) implements Moved {}

    /**
     * Two occurrences that pair with none and have the same last segment of their eIds.
     *
     * @param was the occurrence of the earlier text
     * @param is the occurrence of the later text
     * @param eachAroundPairs whether each occurrence that the two are in pairs
     * @param theOnlyOnes whether no other occurrence that pairs with none has that segment in the
     *     two occurrences that hold the two, the nearest that pair with each other
     */
    record Further(Occurrence was, Occurrence is, boolean eachAroundPairs, boolean theOnlyOnes)
        implements Moved {}
  }

  /**
   * An occurrence that moved pairs with the occurrence that it was.
   *
   * <ul>
   *   <li>Into the occurrence before it, or out of it: two occurrences pair, in order, if they have
   *       the same num, or if neither has a num and they are alike in what they say. For example,
   *       12.4.2.1 stood after 12.4.2 in 2015/1998 until 2021, and is in 12.4.2 since.
   *   <li>Further: two occurrences pair if each occurrence that they are in pairs, no other
   *       occurrence meets the conditions, they have the same num, and they say the same or are
   *       alike from {@link Likeness#MINIMUM}. For example, the consolidation of 692/2008 of 27
   *       July 2017 has point 3.2.12.2.1.11 of Annex XVIII directly in the annex, and the one of 16
   *       May 2016 has it four occurrences down.
   * </ul>
   *
   * @param moved the occurrences that can be one occurrence, moved
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "moved-occurrence")
  static List<Pair> movedOccurrence(Moved moved) {
    return switch (moved) {
      case Moved.IntoOrOutOf(var earlier, var later) -> {
        var earlierNums = earlier.stream().map(HowTwoTextsPair::num).toList();
        var laterNums = later.stream().map(HowTwoTextsPair::num).toList();
        var earlierWords =
            earlier.stream().map(occurrence -> Likeness.words(substantive(occurrence))).toList();
        var laterWords =
            later.stream().map(occurrence -> Likeness.words(substantive(occurrence))).toList();
        var found =
            Likeness.best(
                earlier.size(),
                later.size(),
                (i, j) -> {
                  if (earlierNums.get(i).isEmpty() && laterNums.get(j).isEmpty()) {
                    return Likeness.of(earlierWords.get(i), laterWords.get(j));
                  }
                  return earlierNums.get(i).equals(laterNums.get(j)) ? 1.0 : 0.0;
                });
        var pairs = new ArrayList<Pair>();
        for (var j = 0; j < found.length; j++) {
          if (found[j] >= 0) {
            pairs.add(new Pair(earlier.get(found[j]), later.get(j)));
          }
        }
        yield pairs;
      }
      case Moved.Further further ->
          further.eachAroundPairs()
                  && further.theOnlyOnes()
                  && num(further.was()).equals(num(further.is()))
                  && alikeWithHeadings(further.was(), further.is())
              ? List.of(new Pair(further.was(), further.is()))
              : List.of();
    };
  }

  /**
   * Blocks pair with what they were. The blocks are those between two blocks that say the same in
   * both texts, so each of them differs.
   *
   * <ul>
   *   <li>First, blocks that are alike pair, in order. Of the ways to pair them, the one with the
   *       greatest likeness in all wins. For example, a row changed by one word pairs with the row
   *       that it was, not with a row inserted before it.
   *   <li>Then, between two such pairs, the blocks left with the same name pair in order. For
   *       example, the num "12." with the num "13.".
   * </ul>
   *
   * @param from the blocks of the earlier text
   * @param to the blocks of the later text
   * @return for each block of {@code to}, the position in {@code from} of the block that it pairs
   *     with, or -1 if it pairs with none
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "blocks-pair-with-what-they-were")
  static int[] blocksPairWithWhatTheyWere(List<Element> from, List<Element> to) {
    var sourceOf = new int[to.size()];
    Arrays.fill(sourceOf, -1);
    if ((long) from.size() * to.size() <= Likeness.MOST) {
      Likeness.alike(from, to, sourceOf);
    }
    Likeness.rest(from, to, sourceOf);
    return sourceOf;
  }

  /**
   * The elements that a comparison reads as a sequence of pieces, not as one run of words. They are
   * a table and what is in it: its caption, its rows and groups of rows, its cells, and the lists,
   * occurrences and footnotes in a cell.
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
   * A table compares row by row. It is not one run of words: the comparison reads a table, and what
   * is in it, piece by piece. Thus the words deleted from a cell stay in that cell, and a row
   * inserted or deleted whole shows as a row.
   *
   * @param from an element of the earlier text
   * @param to the element of the later text that it pairs with
   * @return whether the comparison reads the two piece by piece
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "table-row-by-row")
  static boolean tableRowByRow(Element from, Element to) {
    return from.name().equals(to.name()) && PIECES.contains(to.name());
  }

  /** Returns whether the two say the same in their blocks without the num, or are alike in them. */
  private static boolean alikeWithHeadings(Occurrence earlier, Occurrence later) {
    var was = said(earlier, Set.of("num"));
    var is = said(later, Set.of("num"));
    return was.equals(is)
        || Likeness.of(Likeness.words(was), Likeness.words(is)) >= Likeness.MINIMUM;
  }

  /**
   * Returns {@code eId} without the eId of the occurrence {@code in} that it is in, at its start.
   */
  private static String relative(String eId, @Nullable String in) {
    return in != null && eId.startsWith(in + "/") ? eId.substring(in.length() + 1) : eId;
  }

  /** Returns the num of {@code occurrence} as it is matched, without the full stop after it. */
  private static String num(Occurrence occurrence) {
    return occurrence.blocks().stream()
        .filter(block -> block.name().equals("num"))
        .findFirst()
        .map(block -> Wording.key(block).replaceFirst("\\.$", ""))
        .orElse("");
  }

  /**
   * The tag of a renumbering.
   *
   * @param act the amending act. Null if the tag names none.
   * @param tagId the id of the tag, as the consolidation has it. Null if it has none.
   */
  private record Renumbering(@Nullable String act, @Nullable String tagId) {}

  /** Returns the tags of a renumbering on {@code occurrence} and in its num. */
  private static Set<Renumbering> renumberings(Occurrence occurrence) {
    var found = new HashSet<Renumbering>();
    for (var child : occurrence.element().children()) {
      if (child.name().equals("tag")) {
        add(child, found);
      }
    }
    for (var block : occurrence.blocks()) {
      if (block.name().equals("num")) {
        renumberings(block, found);
      }
    }
    return found;
  }

  private static void renumberings(Element element, Set<Renumbering> found) {
    add(element, found);
    for (var child : element.children()) {
      if (Occurrences.eId(child) == null) {
        renumberings(child, found);
      }
    }
  }

  private static void add(Element element, Set<Renumbering> found) {
    if ("renumbered".equals(element.attribute("modification"))) {
      found.add(new Renumbering(element.attribute("amendingAct"), element.attribute("tagId")));
    }
  }

  /**
   * Returns what {@code occurrence} says in its own blocks, without its num and headings, in lower
   * case. A renumbering changes the num, so two occurrences are alike by the rest.
   */
  private static String substantive(Occurrence occurrence) {
    return said(occurrence, TITLES);
  }

  /**
   * Returns what {@code occurrence} says in its own blocks, without the blocks named {@code
   * without}, in lower case.
   */
  private static String said(Occurrence occurrence, Set<String> without) {
    var said = new StringBuilder();
    for (var block : occurrence.blocks()) {
      if (!without.contains(block.name())) {
        said(block, said);
      }
    }
    return said.toString().strip().toLowerCase(Locale.ROOT);
  }

  private static void said(Element element, StringBuilder said) {
    if (element.name().equals("t")) {
      var value = element.attribute("v");
      if (value != null && !value.isBlank()) {
        if (!said.isEmpty()) {
          said.append(' ');
        }
        said.append(value);
      }
      return;
    }
    for (var child : element.children()) {
      if (Occurrences.eId(child) == null) {
        said(child, said);
      }
    }
  }
}
