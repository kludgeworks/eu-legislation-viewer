// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

/**
 * A text's units with an eId (articles, points, annexes…), in document order, each with the unit it
 * is in, the units in it and its own blocks: its num, its heading and the blocks that aren't in a
 * unit of its own, even inside a list of its points (a list's opening words). A unit without an eId
 * is part of the one it is in. Where two units share an eId (as a converted text rarely has), the
 * first is kept.
 *
 * <p>What a consolidation keeps of a deletion (legal-text deleted: the words or units an act
 * deleted, struck, under its marker) is not the text's: a unit in it is not one of its units, and
 * its words are none of a unit's blocks. Which act struck each unit is kept.
 *
 * @param byEId the units, by eId, in document order
 * @param outermost the eIds of the units in no other, in order
 * @param struck the markers of the deletions that keep a unit struck, by its eId: deleted by
 *     2019/103
 */
public record Units(
    SequencedMap<String, Unit> byEId, List<String> outermost, Map<String, List<Marker>> struck) {

  /** The units of {@code text}. */
  public static Units of(Element text) {
    var units = new LinkedHashMap<String, Unit>();
    var outermost = new ArrayList<String>();
    var struck = new HashMap<String, List<Marker>>();
    collect(text, null, null, outermost, units, new ArrayDeque<>(), struck);
    return new Units(units, outermost, struck);
  }

  /** Its unit {@code eId}; none if it has none. */
  public @Nullable Unit get(String eId) {
    return byEId.get(eId);
  }

  /** The eIds of the units directly in the unit {@code parent}, or the outermost ones if none. */
  public List<String> in(@Nullable String parent) {
    if (parent == null) {
      return outermost;
    }
    var unit = get(parent);
    return unit == null ? List.of() : unit.units();
  }

  private static void collect(
      Element element,
      @Nullable String parent,
      @Nullable Element container,
      List<String> siblings,
      Map<String, Unit> units,
      Deque<Ranked> ranked,
      Map<String, List<Marker>> struck) {
    if (element.name().equals("deleted")) {
      struck(element, markers(element), struck);
      return;
    }
    var eId = eId(element);
    if (eId != null && !units.containsKey(eId)) {
      var blocks = new ArrayList<Element>();
      own(element, blocks);
      var section = section(eId, element, parent, units, ranked);
      var list = list(container);
      // in document order, before the units in it: put again once they're known, where it is
      units.put(eId, new Unit(eId, parent, section, element, blocks, List.of(), list));
      siblings.add(eId);
      var inside = new ArrayList<String>();
      for (var child : element.children()) {
        collect(child, eId, element, inside, units, ranked, struck);
      }
      units.put(eId, new Unit(eId, parent, section, element, blocks, inside, list));
      return;
    }
    for (var child : element.children()) {
      collect(child, parent, element, siblings, units, ranked, struck);
    }
  }

  /**
   * The section of the unit {@code eId}: itself if it has a rank, under the last open unit ranked
   * above it, as Outlines nests them; else the last open one whose parent it is in too.
   */
  private static @Nullable String section(
      String eId,
      Element element,
      @Nullable String parent,
      Map<String, Unit> units,
      Deque<Ranked> ranked) {
    var rank = element.attribute("rank");
    if (rank != null) {
      var value = Integer.parseInt(rank);
      while (!ranked.isEmpty() && ranked.peek().rank() >= value) {
        ranked.pop();
      }
      ranked.push(new Ranked(eId, value, parent));
      return eId;
    }
    var enclosing = new HashSet<String>();
    for (var at = parent; at != null; at = Objects.requireNonNull(units.get(at)).parent()) {
      enclosing.add(at);
    }
    for (var open : ranked) {
      if (open.parent() == null || enclosing.contains(open.parent())) {
        return open.eId();
      }
    }
    return null;
  }

  /** {@code container}, if it is a list, without its items; none if it isn't. */
  private static @Nullable Element list(@Nullable Element container) {
    return container == null || !container.name().equals("list")
        ? null
        : new Element(container.name(), container.attributes(), List.of(), container.text());
  }

  /** The deletion markers among {@code deletion}'s children: deleted by 2019/103. */
  private static List<Marker> markers(Element deletion) {
    var markers = new ArrayList<Marker>();
    for (var child : deletion.children()) {
      var act = child.attribute("amendingAct");
      var action = child.attribute("action");
      if (child.name().equals("provenance") && act != null && action != null) {
        markers.add(new Marker(act, action, null));
      }
    }
    return List.copyOf(markers);
  }

  /** The units in {@code element}, the outermost, struck by {@code markers}. */
  private static void struck(
      Element element, List<Marker> markers, Map<String, List<Marker>> struck) {
    for (var child : element.children()) {
      var eId = eId(child);
      if (eId != null) {
        struck.putIfAbsent(eId, markers);
      } else {
        struck(child, markers, struck);
      }
    }
  }

  /** A unit with a rank, still open for those after it. */
  private record Ranked(String eId, int rank, @Nullable String parent) {}

  /** The blocks of {@code element} that aren't in a unit of its own. */
  private static void own(Element element, List<Element> blocks) {
    for (var child : element.children()) {
      if (eId(child) != null
          || child.name().equals("provenance")
          || child.name().equals("deleted")) {
        continue;
      }
      if (holdsUnits(child)) {
        own(child, blocks);
      } else {
        blocks.add(child);
      }
    }
  }

  /** Whether {@code element} has a unit of the text in it, at any depth (not one struck). */
  static boolean holdsUnits(Element element) {
    return element.children().stream()
        .filter(child -> !child.name().equals("deleted"))
        .anyMatch(child -> eId(child) != null || holdsUnits(child));
  }

  /** A unit's eId: a unit's, not a note's or another element's. */
  static @Nullable String eId(Element element) {
    return element.name().equals("unit") ? element.attribute("eId") : null;
  }

  /**
   * A unit.
   *
   * @param eId its eId
   * @param parent the eId of the unit it is in; none for an outermost unit
   * @param section the eId of the unit with a rank it is in, as an outline nests them; itself if it
   *     has a rank; none if it is in none
   * @param element the unit as the text has it, with the units in it
   * @param blocks its own blocks, in order
   * @param units the eIds of the units directly in it, in order
   * @param list the list it is an item of, without its items (its kind: alpha, dash…); none if it
   *     is in none
   */
  public record Unit(
      String eId,
      @Nullable String parent,
      @Nullable String section,
      Element element,
      List<Element> blocks,
      List<String> units,
      @Nullable Element list) {
    public Unit {
      blocks = List.copyOf(blocks);
      units = List.copyOf(units);
    }

    /** What each of its own blocks says. */
    public List<String> wording() {
      return blocks.stream().map(Wording::of).toList();
    }
  }
}
