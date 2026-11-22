// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Pairs the occurrences of one text with the occurrences of the other: which occurrence of the
 * later text is which occurrence of the earlier text.
 *
 * <p>First, occurrences pair with their siblings: the outermost occurrences of the two texts, and
 * the occurrences in two occurrences that pair. Among siblings:
 *
 * <ul>
 *   <li>An occurrence that an act newly renumbered pairs with the earlier occurrence that it was.
 *       That occurrence has another eId and is alike in what it says. Renumbering keeps the order
 *       of the occurrences, so these pairs are in order, with the greatest likeness in all.
 *   <li>Each other occurrence pairs with the earlier occurrence of the same eId.
 * </ul>
 *
 * <p>The eId of an occurrence starts with the eId of the occurrence that it is in. If that
 * occurrence was renumbered, the occurrences in it have other eIds in the two texts. Thus siblings
 * are matched by their eId without that start.
 *
 * <p>Then, an occurrence that moved into the occurrence before it, or out of it, pairs with the
 * occurrence that it was. One text has the occurrences of an occurrence in it, and the other has
 * them after it, as its siblings. For example, 12.4.2.1 stood after 12.4.2 in 2015/1998 until 2021,
 * and is in 12.4.2 since. The occurrences that pair with none among their siblings are matched:
 * those in an occurrence of one text with those that stand right after that occurrence in the other
 * text. Two of them pair, in order, if:
 *
 * <ul>
 *   <li>they have the same num, or
 *   <li>neither has a num, and they are alike in what they say.
 * </ul>
 *
 * <p>Last, an occurrence that moved further pairs with the occurrence that it was: a consolidation
 * can put a occurrence in other occurrences, or take it out of them, without a change to its words.
 * For example, the consolidation of 692/2008 of 27 July 2017 has point 3.2.12.2.1.11 of Annex XVIII
 * directly in the annex, and the one of 16 May 2016 has it four occurrences down. Each occurrence
 * that still pairs with none pairs with the occurrence of the other text that pairs with none and
 * has the same last segment of its eId, if:
 *
 * <ul>
 *   <li>each occurrence that the two are in pairs, so that neither shows in an occurrence that is
 *       inserted or deleted whole,
 *   <li>no other occurrence that pairs with none has that segment in the two occurrences that hold
 *       them, the nearest that pair with each other. The whole text holds them if no such
 *       occurrences do. For example, Annex I of 692/2008 also had a point 3.2.12.2.1.11 until 2017.
 *       It is not in Annex XVIII, so it does not stop the pair.
 *   <li>they have the same num, and say the same, or are alike from {@link Likeness#MINIMUM}.
 * </ul>
 */
final class Pairing {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private final Occurrences before;
  private final Occurrences after;
  private final Map<String, Occurrence> earlierOf = new HashMap<>();
  private final Map<String, Occurrence> laterOf = new HashMap<>();

  private Pairing(Occurrences before, Occurrences after) {
    this.before = before;
    this.after = after;
  }

  /**
   * Pairs the occurrences of {@code after}, the later text, with the occurrences of {@code before}.
   */
  static Pairing of(Occurrences before, Occurrences after) {
    var pairing = new Pairing(before, after);
    pairing.within(null, null);
    pairing.movedFurther();
    return pairing;
  }

  /**
   * Pairs the occurrences that pair with none and moved further than into the occurrence before
   * them or out of it. See the rule above.
   */
  private void movedFurther() {
    var earlier = alone(before, true);
    var later = alone(after, false);
    for (var segment : later.entrySet()) {
      var from = earlier.getOrDefault(segment.getKey(), List.of());
      for (var is : segment.getValue()) {
        for (var was : from) {
          if (is(was) == null
              && was(is) == null
              && inPaired(was, before, true)
              && inPaired(is, after, false)
              && alone(was, is, from, segment.getValue())
              && alike(was, is)) {
            pair(was, is);
            break;
          }
        }
      }
    }
  }

  /**
   * Returns whether {@code was} and {@code is} are the only occurrences of {@code earlier} and
   * {@code later} in the two occurrences that hold them, the nearest that pair with each other.
   * {@code earlier} and {@code later} are the occurrences that pair with none and have their
   * segment.
   */
  private boolean alone(
      Occurrence was, Occurrence is, List<Occurrence> earlier, List<Occurrence> later) {
    var holder = after.get(Objects.requireNonNullElse(is.parent(), ""));
    while (holder != null) {
      var paired = was(holder);
      if (paired != null && holds(before, paired, was)) {
        break;
      }
      holder = after.get(Objects.requireNonNullElse(holder.parent(), ""));
    }
    var earlierHolder = holder == null ? null : was(holder);
    var at = holder;
    return earlier.stream().filter(each -> holds(before, earlierHolder, each)).count() == 1
        && later.stream().filter(each -> holds(after, at, each)).count() == 1;
  }

  /**
   * Returns whether {@code holder} of {@code occurrences} holds {@code occurrence}. The whole text,
   * a null {@code holder}, holds each occurrence.
   */
  private static boolean holds(
      Occurrences occurrences, @Nullable Occurrence holder, Occurrence occurrence) {
    if (holder == null) {
      return true;
    }
    for (var at = occurrence.parent(); at != null; ) {
      if (at.equals(holder.eId())) {
        return true;
      }
      var up = occurrences.get(at);
      at = up == null ? null : up.parent();
    }
    return false;
  }

  /**
   * Returns the occurrences of {@code occurrences} that pair with none, by the last segment of
   * their eIds, in document order. {@code earlier} tells whether they are the occurrences of the
   * earlier text.
   */
  private Map<String, List<Occurrence>> alone(Occurrences occurrences, boolean earlier) {
    var found = new LinkedHashMap<String, List<Occurrence>>();
    for (var occurrence : occurrences.byEId().values()) {
      if ((earlier ? is(occurrence) : was(occurrence)) == null) {
        found.computeIfAbsent(segment(occurrence.eId()), each -> new ArrayList<>()).add(occurrence);
      }
    }
    return found;
  }

  /** Returns the last segment of {@code eId}. For example, pnt_3.2 for anx/pnt_3/pnt_3.2. */
  private static String segment(String eId) {
    return eId.substring(eId.lastIndexOf('/') + 1);
  }

  /**
   * Returns whether each occurrence that {@code occurrence} of {@code occurrences} is in pairs.
   * {@code earlier} tells whether they are the occurrences of the earlier text.
   */
  private boolean inPaired(Occurrence occurrence, Occurrences occurrences, boolean earlier) {
    for (var at = occurrence.parent(); at != null; ) {
      var holder = occurrences.get(at);
      if (holder == null || (earlier ? is(holder) : was(holder)) == null) {
        return false;
      }
      at = holder.parent();
    }
    return true;
  }

  /**
   * Returns whether {@code earlier} and {@code later} have the same num, and say the same in their
   * other blocks or are alike in them.
   */
  private static boolean alike(Occurrence earlier, Occurrence later) {
    if (!num(earlier).equals(num(later))) {
      return false;
    }
    var was = titled(earlier);
    var is = titled(later);
    return was.equals(is)
        || Likeness.of(Likeness.words(was), Likeness.words(is)) >= Likeness.MINIMUM;
  }

  /**
   * Returns the earlier occurrence that {@code later} pairs with. Returns null if it pairs with
   * none.
   */
  @Nullable Occurrence was(Occurrence later) {
    return earlierOf.get(later.eId());
  }

  /**
   * Returns the later occurrence that {@code earlier} pairs with. Returns null if it pairs with
   * none.
   */
  @Nullable Occurrence is(Occurrence earlier) {
    return laterOf.get(earlier.eId());
  }

  /**
   * The occurrences in an occurrence that pair with none.
   *
   * @param earlier those of the earlier text, in order
   * @param later those of the later text, in order
   */
  private record Unpaired(List<Occurrence> earlier, List<Occurrence> later) {}

  /**
   * Pairs the occurrences in the occurrence {@code was} of the earlier text with the occurrences in
   * the occurrence {@code is} of the later text, and the occurrences in them in turn. If both are
   * null, pairs the outermost occurrences. Returns the occurrences directly in the two that pair
   * with none.
   */
  private Unpaired within(@Nullable Occurrence was, @Nullable Occurrence is) {
    var earlier = in(before, was);
    var later = in(after, is);
    var sourceOf =
        siblings(earlier, later, was == null ? null : was.eId(), is == null ? null : is.eId());
    var targetOf = new int[earlier.size()];
    Arrays.fill(targetOf, -1);
    var inside = new HashMap<Integer, Unpaired>();
    for (var j = 0; j < later.size(); j++) {
      if (sourceOf[j] >= 0) {
        targetOf[sourceOf[j]] = j;
        inside.put(j, pair(earlier.get(sourceOf[j]), later.get(j)));
      }
    }
    for (var entry : inside.entrySet()) {
      var j = entry.getKey();
      // Moved in: the occurrences stood after the earlier occurrence, and are in the later
      // occurrence.
      moved(after(earlier, targetOf, sourceOf[j]), entry.getValue().later());
      // Moved out: the occurrences were in the earlier occurrence, and stand after the later
      // occurrence.
      moved(entry.getValue().earlier(), after(later, sourceOf, j));
    }
    return new Unpaired(
        earlier.stream().filter(occurrence -> is(occurrence) == null).toList(),
        later.stream().filter(occurrence -> was(occurrence) == null).toList());
  }

  /** Pairs {@code earlier} with {@code later}, and the occurrences in them. See {@link #within}. */
  private Unpaired pair(Occurrence earlier, Occurrence later) {
    earlierOf.put(later.eId(), earlier);
    laterOf.put(earlier.eId(), later);
    return within(earlier, later);
  }

  /**
   * Pairs the occurrences of {@code earlier} and {@code later} that are one occurrence, moved.
   * Occurrences that pair already are left out.
   */
  private void moved(List<Occurrence> earlier, List<Occurrence> later) {
    var from = earlier.stream().filter(occurrence -> is(occurrence) == null).toList();
    var to = later.stream().filter(occurrence -> was(occurrence) == null).toList();
    if (from.isEmpty() || to.isEmpty()) {
      return;
    }
    var fromNums = from.stream().map(Pairing::num).toList();
    var toNums = to.stream().map(Pairing::num).toList();
    var fromWords =
        from.stream().map(occurrence -> Likeness.words(substantive(occurrence))).toList();
    var toWords = to.stream().map(occurrence -> Likeness.words(substantive(occurrence))).toList();
    var sourceOf =
        Likeness.best(
            from.size(),
            to.size(),
            (i, j) -> {
              if (fromNums.get(i).isEmpty() && toNums.get(j).isEmpty()) {
                return Likeness.of(fromWords.get(i), toWords.get(j));
              }
              return fromNums.get(i).equals(toNums.get(j)) ? 1.0 : 0.0;
            });
    for (var j = 0; j < sourceOf.length; j++) {
      if (sourceOf[j] >= 0) {
        pair(from.get(sourceOf[j]), to.get(j));
      }
    }
  }

  /**
   * Returns the occurrences of {@code occurrences} that stand right after the occurrence at the
   * position {@code at} and pair with none among their siblings: up to the next occurrence that
   * pairs. {@code pairOf} has, for each occurrence, the position of the sibling that it pairs with,
   * or -1.
   */
  private static List<Occurrence> after(List<Occurrence> occurrences, int[] pairOf, int at) {
    var found = new ArrayList<Occurrence>();
    for (var i = at + 1; i < occurrences.size() && pairOf[i] < 0; i++) {
      found.add(occurrences.get(i));
    }
    return found;
  }

  /**
   * Returns the occurrences directly in {@code occurrence} of {@code occurrences}, or its outermost
   * occurrences.
   */
  private static List<Occurrence> in(Occurrences occurrences, @Nullable Occurrence occurrence) {
    var found = new ArrayList<Occurrence>();
    for (var eId : occurrences.in(occurrence == null ? null : occurrence.eId())) {
      var each = occurrences.get(eId);
      if (each != null) {
        found.add(each);
      }
    }
    return found;
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
   * Pairs the sibling occurrences {@code earlier} of one text with the sibling occurrences {@code
   * later} of the other.
   *
   * @param earlierIn the eId of the occurrence that {@code earlier} are in. Null for outermost
   *     occurrences.
   * @param laterIn the eId of the occurrence that {@code later} are in. Null for outermost
   *     occurrences.
   * @return for each occurrence of {@code later}, the position in {@code earlier} of the occurrence
   *     that it pairs with, or -1 if it pairs with none
   */
  private static int[] siblings(
      List<Occurrence> earlier,
      List<Occurrence> later,
      @Nullable String earlierIn,
      @Nullable String laterIn) {
    var earlierIds =
        earlier.stream().map(occurrence -> relative(occurrence.eId(), earlierIn)).toList();
    var laterIds = later.stream().map(occurrence -> relative(occurrence.eId(), laterIn)).toList();
    var position = new HashMap<String, Integer>();
    for (var i = 0; i < earlierIds.size(); i++) {
      position.put(earlierIds.get(i), i);
    }
    var renumbered = new boolean[later.size()];
    for (var j = 0; j < later.size(); j++) {
      var same = position.get(laterIds.get(j));
      renumbered[j] = newlyRenumbered(later.get(j), same == null ? null : earlier.get(same));
    }
    var sourceOf = renumbered(earlier, later, earlierIds, laterIds, renumbered);
    var paired = new boolean[earlier.size()];
    for (var source : sourceOf) {
      if (source >= 0) {
        paired[source] = true;
      }
    }
    for (var j = 0; j < later.size(); j++) {
      var same = position.get(laterIds.get(j));
      if (!renumbered[j] && same != null && !paired[same]) {
        sourceOf[j] = same;
        paired[same] = true;
      }
    }
    return sourceOf;
  }

  /**
   * Pairs the newly renumbered occurrences of {@code later} with the occurrences of {@code earlier}
   * that they were. Two occurrences can pair if their eIds differ and they say the same, or are
   * alike from {@link Likeness#MINIMUM}.
   */
  private static int[] renumbered(
      List<Occurrence> earlier,
      List<Occurrence> later,
      List<String> earlierIds,
      List<String> laterIds,
      boolean[] renumbered) {
    var sourceOf = new int[later.size()];
    Arrays.fill(sourceOf, -1);
    var candidates = new ArrayList<Integer>();
    for (var j = 0; j < later.size(); j++) {
      if (renumbered[j]) {
        candidates.add(j);
      }
    }
    if (candidates.isEmpty()) {
      return sourceOf;
    }
    var earlierSaid = earlier.stream().map(Pairing::substantive).toList();
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
    for (var k = 0; k < found.length; k++) {
      sourceOf[candidates.get(k)] = found[k];
    }
    return sourceOf;
  }

  /**
   * Returns whether an act renumbered {@code occurrence} between the two texts. It then has a tag
   * of a renumbering that {@code same}, the earlier occurrence of the same eId, does not have.
   * {@code same} is null if the earlier text has no such occurrence.
   */
  private static boolean newlyRenumbered(Occurrence occurrence, @Nullable Occurrence same) {
    var tags = renumberings(occurrence);
    return !tags.isEmpty() && !tags.equals(same == null ? Set.of() : renumberings(same));
  }

  /**
   * Returns {@code eId} without the eId of the occurrence {@code in} that it is in, at its start.
   */
  private static String relative(String eId, @Nullable String in) {
    return in != null && eId.startsWith(in + "/") ? eId.substring(in.length() + 1) : eId;
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
   * Returns what {@code occurrence} says in its own blocks, with its headings but without its num.
   */
  private static String titled(Occurrence occurrence) {
    return said(occurrence, Set.of("num"));
  }

  /**
   * Returns what {@code occurrence} says in its own blocks, without the blocks named {@code
   * without}, in lower case.
   */
  private static String said(Occurrence occurrence, Set<String> without) {
    var said = new StringBuilder();
    for (var block : occurrence.blocks()) {
      if (!without.contains(block.name())) {
        substantive(block, said);
      }
    }
    return said.toString().strip().toLowerCase(Locale.ROOT);
  }

  private static void substantive(Element element, StringBuilder said) {
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
        substantive(child, said);
      }
    }
  }
}
