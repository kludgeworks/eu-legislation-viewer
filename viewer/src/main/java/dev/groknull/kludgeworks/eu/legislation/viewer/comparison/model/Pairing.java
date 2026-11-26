// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Moved;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Pair;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.HowTwoTextsPair.Siblings;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The pairing of two texts: which occurrence of the later text is which occurrence of the earlier
 * text. {@link #of} applies the rules of {@link HowTwoTextsPair} in three stages. Each stage is a
 * function of the two texts and the pairs so far, and gives the pairs that it finds. A stage pairs
 * only occurrences that pair with none yet.
 *
 * <ol>
 *   <li><b>Among siblings.</b> The occurrences in two occurrences that pair, then the occurrences
 *       in each of those pairs, to the innermost. Among siblings, the renumbered occurrences pair
 *       first, then each other occurrence by its eId.
 *   <li><b>Moved into the occurrence before it, or out of it.</b> For each pair of stage 1: the
 *       occurrences in one of the two pair with the occurrences that stand right after the other.
 *   <li><b>Moved further.</b> Each occurrence that still pairs with none, in the order of the later
 *       text.
 * </ol>
 *
 * <p>The occurrences in a pair of stage 2 or 3 pair with none yet. Thus {@link #of} does the stages
 * again for them, until no stage finds a pair.
 */
final class Pairing {
  private static final Pairing NONE = new Pairing(Map.of(), Map.of());

  private final Map<String, Occurrence> earlierOf;
  private final Map<String, Occurrence> laterOf;

  private Pairing(Map<String, Occurrence> earlierOf, Map<String, Occurrence> laterOf) {
    this.earlierOf = earlierOf;
    this.laterOf = laterOf;
  }

  /**
   * Pairs the occurrences of {@code after}, the later text, with the occurrences of {@code before}.
   */
  static Pairing of(Occurrences before, Occurrences after) {
    var pairing = settled(before, after, NONE, amongSiblings(before, after, null));
    for (var further = movedFurther(before, after, pairing);
        !further.isEmpty();
        further = movedFurther(before, after, pairing)) {
      pairing = settled(before, after, pairing.with(further), within(before, after, further));
    }
    return pairing;
  }

  /**
   * Returns {@code pairing} with {@code pairs}, the pairs that stage 1 found, and with each pair
   * that stages 2 and 1 then find in turn, until stage 2 finds none.
   */
  private static Pairing settled(
      Occurrences before, Occurrences after, Pairing pairing, List<Among> pairs) {
    var found = pairing.with(pairs.stream().map(Among::pair).toList());
    var open = pairs;
    while (!open.isEmpty()) {
      var moved = movedIntoOrOutOf(before, after, found, open);
      open = within(before, after, moved);
      found = found.with(moved).with(open.stream().map(Among::pair).toList());
    }
    return found;
  }

  /** Returns what stage 1 finds in each of {@code pairs}. */
  private static List<Among> within(Occurrences before, Occurrences after, List<Pair> pairs) {
    var found = new ArrayList<Among>();
    for (var pair : pairs) {
      amongSiblings(before, after, pair, found);
    }
    return found;
  }

  /** Returns this pairing with {@code pairs} too. */
  private Pairing with(List<Pair> pairs) {
    if (pairs.isEmpty()) {
      return this;
    }
    var earlier = new HashMap<>(earlierOf);
    var later = new HashMap<>(laterOf);
    for (var pair : pairs) {
      earlier.put(pair.is().eId(), pair.was());
      later.put(pair.was().eId(), pair.is());
    }
    return new Pairing(earlier, later);
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

  /** The pairs that one stage found so far, and this pairing before the stage. */
  private final class Found {
    private final List<Pair> pairs = new ArrayList<>();
    private final Set<String> earlier = new HashSet<>();
    private final Set<String> later = new HashSet<>();

    void add(Pair pair) {
      pairs.add(pair);
      earlier.add(pair.was().eId());
      later.add(pair.is().eId());
    }

    /** Returns whether {@code occurrence} of the earlier text pairs. */
    boolean pairsEarlier(Occurrence occurrence) {
      return is(occurrence) != null || earlier.contains(occurrence.eId());
    }

    /** Returns whether {@code occurrence} of the later text pairs. */
    boolean pairsLater(Occurrence occurrence) {
      return was(occurrence) != null || later.contains(occurrence.eId());
    }
  }

  /**
   * Stage 1. Pairs the occurrences in the two occurrences of {@code within}, and the occurrences in
   * each pair in turn. If {@code within} is null, pairs the outermost occurrences of the two texts.
   * None of these occurrences pairs yet. Returns the pairs, the innermost first.
   */
  private static List<Among> amongSiblings(
      Occurrences before, Occurrences after, @Nullable Pair within) {
    var found = new ArrayList<Among>();
    amongSiblings(before, after, within, found);
    return found;
  }

  private static void amongSiblings(
      Occurrences before, Occurrences after, @Nullable Pair within, List<Among> found) {
    var was = within == null ? null : within.was();
    var is = within == null ? null : within.is();
    var siblings =
        new Siblings(
            in(before, was),
            in(after, is),
            was == null ? null : was.eId(),
            is == null ? null : is.eId());
    var renumbered = HowTwoTextsPair.renumberedOccurrence(siblings);
    var pairs = new ArrayList<>(renumbered);
    pairs.addAll(HowTwoTextsPair.placeAmongSiblings(siblings, renumbered));
    var earlier = pairs.stream().map(pair -> pair.was().eId()).collect(Collectors.toSet());
    var later = pairs.stream().map(pair -> pair.is().eId()).collect(Collectors.toSet());
    var here = new ArrayList<Among>();
    for (var pair : pairs) {
      amongSiblings(before, after, pair, found);
      here.add(
          new Among(
              pair,
              after(siblings.earlier(), pair.was(), earlier),
              after(siblings.later(), pair.is(), later)));
    }
    found.addAll(here);
  }

  /**
   * Returns the occurrences of {@code siblings} that stand right after {@code occurrence} and pair
   * with none: up to the next sibling that pairs. {@code paired} holds the eIds of the siblings
   * that pair.
   */
  private static List<Occurrence> after(
      List<Occurrence> siblings, Occurrence occurrence, Set<String> paired) {
    var found = new ArrayList<Occurrence>();
    for (var i = siblings.indexOf(occurrence) + 1; i < siblings.size(); i++) {
      var each = siblings.get(i);
      if (paired.contains(each.eId())) {
        break;
      }
      found.add(each);
    }
    return found;
  }

  /**
   * Stage 2. For each of {@code pairs}, pairs the occurrences that moved into the later occurrence
   * or out of the earlier one. {@code pairing} holds the pairs so far. Returns the pairs that it
   * finds.
   */
  private static List<Pair> movedIntoOrOutOf(
      Occurrences before, Occurrences after, Pairing pairing, List<Among> pairs) {
    var found = pairing.new Found();
    for (var among : pairs) {
      // Moved in: the occurrences stood after the earlier occurrence, and are in the later one.
      moved(among.earlierAfter(), in(after, among.pair().is()), found);
      // Moved out: the occurrences were in the earlier occurrence, and stand after the later one.
      moved(in(before, among.pair().was()), among.laterAfter(), found);
    }
    return found.pairs;
  }

  private static void moved(List<Occurrence> earlier, List<Occurrence> later, Found found) {
    var from = earlier.stream().filter(occurrence -> !found.pairsEarlier(occurrence)).toList();
    var to = later.stream().filter(occurrence -> !found.pairsLater(occurrence)).toList();
    if (from.isEmpty() || to.isEmpty()) {
      return;
    }
    HowTwoTextsPair.movedOccurrence(new Moved.IntoOrOutOf(from, to)).forEach(found::add);
  }

  /**
   * Stage 3. Pairs the occurrences that pair with none and moved further. {@code pairing} holds the
   * pairs so far. Returns the pairs that it finds.
   */
  private static List<Pair> movedFurther(Occurrences before, Occurrences after, Pairing pairing) {
    var found = pairing.new Found();
    var earlier = pairing.alone(before, true);
    var later = pairing.alone(after, false);
    for (var segment : later.entrySet()) {
      var from = earlier.getOrDefault(segment.getKey(), List.of());
      for (var is : segment.getValue()) {
        for (var was : from) {
          if (found.pairsEarlier(was) || found.pairsLater(is)) {
            continue;
          }
          var eachAroundPairs =
              pairing.inPaired(was, before, true) && pairing.inPaired(is, after, false);
          var further =
              new Moved.Further(
                  was,
                  is,
                  eachAroundPairs,
                  eachAroundPairs
                      && pairing.alone(before, after, was, is, from, segment.getValue()));
          var pairs = HowTwoTextsPair.movedOccurrence(further);
          if (!pairs.isEmpty()) {
            found.add(pairs.getFirst());
            break;
          }
        }
      }
    }
    return found.pairs;
  }

  /**
   * Returns whether {@code was} and {@code is} are the only occurrences of {@code earlier} and
   * {@code later} in the two occurrences that hold them, the nearest that pair with each other.
   * {@code earlier} and {@code later} are the occurrences that pair with none and have their
   * segment.
   */
  private boolean alone(
      Occurrences before,
      Occurrences after,
      Occurrence was,
      Occurrence is,
      List<Occurrence> earlier,
      List<Occurrence> later) {
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
