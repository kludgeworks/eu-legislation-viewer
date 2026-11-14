// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Units.Unit;
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
 * Pairs the units of one text with the units of the other: which unit of the later text is which
 * unit of the earlier text.
 *
 * <p>First, units pair with their siblings: the outermost units of the two texts, and the units in
 * two units that pair. Among siblings:
 *
 * <ul>
 *   <li>A unit that an act newly renumbered pairs with the earlier unit that it was. That unit has
 *       another eId and is alike in what it says. Renumbering keeps the order of the units, so
 *       these pairs are in order, with the greatest likeness in all.
 *   <li>Each other unit pairs with the earlier unit of the same eId.
 * </ul>
 *
 * <p>The eId of a unit starts with the eId of the unit that it is in. If that unit was renumbered,
 * the units in it have other eIds in the two texts. Thus siblings are matched by their eId without
 * that start.
 *
 * <p>Then, a unit that moved into the unit before it, or out of it, pairs with the unit that it
 * was. One text has the units of a unit in it, and the other has them after it, as its siblings.
 * For example, 12.4.2.1 stood after 12.4.2 in 2015/1998 until 2021, and is in 12.4.2 since. The
 * units that pair with none among their siblings are matched: those in a unit of one text with
 * those that stand right after that unit in the other text. Two of them pair, in order, if:
 *
 * <ul>
 *   <li>they have the same num, or
 *   <li>neither has a num, and they are alike in what they say.
 * </ul>
 */
final class Pairing {
  private static final Set<String> TITLES = Set.of("num", "heading", "subheading");

  private final Units before;
  private final Units after;
  private final Map<String, Unit> earlierOf = new HashMap<>();
  private final Map<String, Unit> laterOf = new HashMap<>();

  private Pairing(Units before, Units after) {
    this.before = before;
    this.after = after;
  }

  /** Pairs the units of {@code after}, the later text, with the units of {@code before}. */
  static Pairing of(Units before, Units after) {
    var pairing = new Pairing(before, after);
    pairing.within(null, null);
    return pairing;
  }

  /** Returns the earlier unit that {@code later} pairs with. Returns null if it pairs with none. */
  @Nullable Unit was(Unit later) {
    return earlierOf.get(later.eId());
  }

  /** Returns the later unit that {@code earlier} pairs with. Returns null if it pairs with none. */
  @Nullable Unit is(Unit earlier) {
    return laterOf.get(earlier.eId());
  }

  /**
   * The units in a unit that pair with none.
   *
   * @param earlier those of the earlier text, in order
   * @param later those of the later text, in order
   */
  private record Unpaired(List<Unit> earlier, List<Unit> later) {}

  /**
   * Pairs the units in the unit {@code was} of the earlier text with the units in the unit {@code
   * is} of the later text, and the units in them in turn. If both are null, pairs the outermost
   * units. Returns the units directly in the two that pair with none.
   */
  private Unpaired within(@Nullable Unit was, @Nullable Unit is) {
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
      // Moved in: the units stood after the earlier unit, and are in the later unit.
      moved(after(earlier, targetOf, sourceOf[j]), entry.getValue().later());
      // Moved out: the units were in the earlier unit, and stand after the later unit.
      moved(entry.getValue().earlier(), after(later, sourceOf, j));
    }
    return new Unpaired(
        earlier.stream().filter(unit -> is(unit) == null).toList(),
        later.stream().filter(unit -> was(unit) == null).toList());
  }

  /** Pairs {@code earlier} with {@code later}, and the units in them. See {@link #within}. */
  private Unpaired pair(Unit earlier, Unit later) {
    earlierOf.put(later.eId(), earlier);
    laterOf.put(earlier.eId(), later);
    return within(earlier, later);
  }

  /**
   * Pairs the units of {@code earlier} and {@code later} that are one unit, moved. Units that pair
   * already are left out.
   */
  private void moved(List<Unit> earlier, List<Unit> later) {
    var from = earlier.stream().filter(unit -> is(unit) == null).toList();
    var to = later.stream().filter(unit -> was(unit) == null).toList();
    if (from.isEmpty() || to.isEmpty()) {
      return;
    }
    var fromNums = from.stream().map(Pairing::num).toList();
    var toNums = to.stream().map(Pairing::num).toList();
    var fromWords = from.stream().map(unit -> Likeness.words(substantive(unit))).toList();
    var toWords = to.stream().map(unit -> Likeness.words(substantive(unit))).toList();
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
   * Returns the units of {@code units} that stand right after the unit at the position {@code at}
   * and pair with none among their siblings: up to the next unit that pairs. {@code pairOf} has,
   * for each unit, the position of the sibling that it pairs with, or -1.
   */
  private static List<Unit> after(List<Unit> units, int[] pairOf, int at) {
    var found = new ArrayList<Unit>();
    for (var i = at + 1; i < units.size() && pairOf[i] < 0; i++) {
      found.add(units.get(i));
    }
    return found;
  }

  /** Returns the units directly in {@code unit} of {@code units}, or its outermost units. */
  private static List<Unit> in(Units units, @Nullable Unit unit) {
    var found = new ArrayList<Unit>();
    for (var eId : units.in(unit == null ? null : unit.eId())) {
      var each = units.get(eId);
      if (each != null) {
        found.add(each);
      }
    }
    return found;
  }

  /** Returns the num of {@code unit} as it is matched, without the full stop after it. */
  private static String num(Unit unit) {
    return unit.blocks().stream()
        .filter(block -> block.name().equals("num"))
        .findFirst()
        .map(block -> Wording.key(block).replaceFirst("\\.$", ""))
        .orElse("");
  }

  /**
   * Pairs the sibling units {@code earlier} of one text with the sibling units {@code later} of the
   * other.
   *
   * @param earlierIn the eId of the unit that {@code earlier} are in. Null for outermost units.
   * @param laterIn the eId of the unit that {@code later} are in. Null for outermost units.
   * @return for each unit of {@code later}, the position in {@code earlier} of the unit that it
   *     pairs with, or -1 if it pairs with none
   */
  private static int[] siblings(
      List<Unit> earlier, List<Unit> later, @Nullable String earlierIn, @Nullable String laterIn) {
    var earlierIds = earlier.stream().map(unit -> relative(unit.eId(), earlierIn)).toList();
    var laterIds = later.stream().map(unit -> relative(unit.eId(), laterIn)).toList();
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
   * Pairs the newly renumbered units of {@code later} with the units of {@code earlier} that they
   * were. Two units can pair if their eIds differ and they say the same, or are alike from {@link
   * Likeness#MINIMUM}.
   */
  private static int[] renumbered(
      List<Unit> earlier,
      List<Unit> later,
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
   * Returns whether an act renumbered {@code unit} between the two texts. It then has a marker of a
   * renumbering that {@code same}, the earlier unit of the same eId, does not have. {@code same} is
   * null if the earlier text has no such unit.
   */
  private static boolean newlyRenumbered(Unit unit, @Nullable Unit same) {
    var markers = renumberings(unit);
    return !markers.isEmpty() && !markers.equals(same == null ? Set.of() : renumberings(same));
  }

  /** Returns {@code eId} without the eId of the unit {@code in} that it is in, at its start. */
  private static String relative(String eId, @Nullable String in) {
    return in != null && eId.startsWith(in + "/") ? eId.substring(in.length() + 1) : eId;
  }

  /**
   * The marker of a renumbering.
   *
   * @param act the amending act. Null if the marker names none.
   * @param marker the marker as the consolidation has it. Null if it has none.
   */
  private record Renumbering(@Nullable String act, @Nullable String marker) {}

  /** Returns the markers of a renumbering on {@code unit} and in its num. */
  private static Set<Renumbering> renumberings(Unit unit) {
    var found = new HashSet<Renumbering>();
    for (var child : unit.element().children()) {
      if (child.name().equals("provenance")) {
        add(child, found);
      }
    }
    for (var block : unit.blocks()) {
      if (block.name().equals("num")) {
        renumberings(block, found);
      }
    }
    return found;
  }

  private static void renumberings(Element element, Set<Renumbering> found) {
    add(element, found);
    for (var child : element.children()) {
      if (Units.eId(child) == null) {
        renumberings(child, found);
      }
    }
  }

  private static void add(Element element, Set<Renumbering> found) {
    if ("renumbered".equals(element.attribute("action"))) {
      found.add(new Renumbering(element.attribute("amendingAct"), element.attribute("marker")));
    }
  }

  /**
   * Returns what {@code unit} says in its own blocks, without its num and headings, in lower case.
   * A renumbering changes the num, so two units are alike by the rest.
   */
  private static String substantive(Unit unit) {
    var said = new StringBuilder();
    for (var block : unit.blocks()) {
      if (!TITLES.contains(block.name())) {
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
      if (Units.eId(child) == null) {
        substantive(child, said);
      }
    }
  }
}
