// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Moved;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Pair;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Siblings;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The pairing of two texts: which occurrence of the later text is which occurrence of the earlier
 * text. {@link #of} applies the rules of {@link HowTwoTextsPair} in three stages. A stage pairs
 * only occurrences that pair with none yet.
 *
 * <ol>
 *   <li><b>Among siblings.</b> The outermost occurrences of the two texts pair, then the
 *       occurrences in each two occurrences that pair, to the innermost. Among siblings, the
 *       renumbered occurrences pair first, then each other occurrence by its eId.
 *   <li><b>Moved into the occurrence before it, or out of it.</b> For each pair, from the innermost
 *       to the outermost: the occurrences in one of the two pair with the occurrences that stand
 *       right after the other. The occurrences in a new pair then go through stages 1 and 2.
 *   <li><b>Moved further.</b> Each occurrence that still pairs with none, in the order of the later
 *       text. The occurrences in a new pair then go through stages 1 and 2.
 * </ol>
 */
final class Pairing {
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
    var pairs = pairing.amongSiblings(null, null);
    pairing.movedIntoOrOutOf(pairs);
    pairing.movedFurther();
    return pairing;
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
   * Two occurrences that pair, among their siblings.
   *
   * @param pair the two
   * @param earlierAfter the siblings that stand right after the earlier one and pair with none
   *     among their siblings: up to the next sibling that pairs
   * @param laterAfter the same for the later one
   */
  private record Among(Pair pair, List<Occurrence> earlierAfter, List<Occurrence> laterAfter) {}

  /**
   * Stage 1. Pairs the occurrences in the occurrence {@code was} of the earlier text with those in
   * the occurrence {@code is} of the later text, and the occurrences in each pair in turn. If both
   * are null, pairs the outermost occurrences. Returns the pairs, the innermost first.
   */
  private List<Among> amongSiblings(@Nullable Occurrence was, @Nullable Occurrence is) {
    var found = new ArrayList<Among>();
    amongSiblings(was, is, found);
    return found;
  }

  private void amongSiblings(@Nullable Occurrence was, @Nullable Occurrence is, List<Among> found) {
    var siblings =
        new Siblings(
            in(before, was),
            in(after, is),
            was == null ? null : was.eId(),
            is == null ? null : is.eId());
    var renumbered = HowTwoTextsPair.renumberedOccurrence(siblings);
    var pairs = new ArrayList<>(renumbered);
    pairs.addAll(HowTwoTextsPair.placeAmongSiblings(siblings, renumbered));
    pairs.forEach(this::pair);
    var here = new ArrayList<Among>();
    for (var pair : pairs) {
      amongSiblings(pair.was(), pair.is(), found);
      here.add(
          new Among(
              pair,
              after(siblings.earlier(), pair.was(), true),
              after(siblings.later(), pair.is(), false)));
    }
    found.addAll(here);
  }

  /**
   * Returns the occurrences of {@code siblings} that stand right after {@code occurrence} and pair
   * with none: up to the next sibling that pairs. {@code earlier} tells whether they are
   * occurrences of the earlier text.
   */
  private List<Occurrence> after(
      List<Occurrence> siblings, Occurrence occurrence, boolean earlier) {
    var found = new ArrayList<Occurrence>();
    for (var i = siblings.indexOf(occurrence) + 1; i < siblings.size(); i++) {
      var each = siblings.get(i);
      if ((earlier ? is(each) : was(each)) != null) {
        break;
      }
      found.add(each);
    }
    return found;
  }

  /**
   * Stage 2. For each of {@code pairs}, pairs the occurrences that moved into the later occurrence
   * or out of the earlier one.
   */
  private void movedIntoOrOutOf(List<Among> pairs) {
    for (var among : pairs) {
      // Moved in: the occurrences stood after the earlier occurrence, and are in the later one.
      moved(among.earlierAfter(), in(after, among.pair().is()));
      // Moved out: the occurrences were in the earlier occurrence, and stand after the later one.
      moved(in(before, among.pair().was()), among.laterAfter());
    }
  }

  private void moved(List<Occurrence> earlier, List<Occurrence> later) {
    var from = earlier.stream().filter(occurrence -> is(occurrence) == null).toList();
    var to = later.stream().filter(occurrence -> was(occurrence) == null).toList();
    if (from.isEmpty() || to.isEmpty()) {
      return;
    }
    for (var pair : HowTwoTextsPair.movedOccurrence(new Moved.IntoOrOutOf(from, to))) {
      pairWithin(pair);
    }
  }

  /** Stage 3. Pairs the occurrences that pair with none and moved further. */
  private void movedFurther() {
    var earlier = alone(before, true);
    var later = alone(after, false);
    for (var segment : later.entrySet()) {
      var from = earlier.getOrDefault(segment.getKey(), List.of());
      for (var is : segment.getValue()) {
        for (var was : from) {
          if (is(was) != null || was(is) != null) {
            continue;
          }
          var eachAroundPairs = inPaired(was, before, true) && inPaired(is, after, false);
          var further =
              new Moved.Further(
                  was,
                  is,
                  eachAroundPairs,
                  eachAroundPairs && alone(was, is, from, segment.getValue()));
          var pairs = HowTwoTextsPair.movedOccurrence(further);
          if (!pairs.isEmpty()) {
            pairWithin(pairs.getFirst());
            break;
          }
        }
      }
    }
  }

  /** Pairs the two, and puts the occurrences in them through stages 1 and 2. */
  private void pairWithin(Pair pair) {
    pair(pair);
    movedIntoOrOutOf(amongSiblings(pair.was(), pair.is()));
  }

  private void pair(Pair pair) {
    earlierOf.put(pair.is().eId(), pair.was());
    laterOf.put(pair.was().eId(), pair.is());
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
}
