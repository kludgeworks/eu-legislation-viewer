// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * @param byEId the units, by eId, in document order
 * @param outermost the eIds of the units in no other, in order
 */
public record Units(SequencedMap<String, Unit> byEId, List<String> outermost) {

  /** The units of {@code text}. */
  public static Units of(Element text) {
    var units = new LinkedHashMap<String, Unit>();
    var outermost = new ArrayList<String>();
    collect(text, null, null, outermost, units, new ArrayDeque<>());
    return new Units(units, outermost);
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
      Deque<Ranked> ranked) {
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
        collect(child, eId, element, inside, units, ranked);
      }
      units.put(eId, new Unit(eId, parent, section, element, blocks, inside, list));
      return;
    }
    for (var child : element.children()) {
      collect(child, parent, element, siblings, units, ranked);
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

  /** A unit with a rank, still open for those after it. */
  private record Ranked(String eId, int rank, @Nullable String parent) {}

  /** The blocks of {@code element} that aren't in a unit of its own. */
  private static void own(Element element, List<Element> blocks) {
    for (var child : element.children()) {
      if (eId(child) != null || child.name().equals("provenance")) {
        continue;
      }
      if (holdsUnits(child)) {
        own(child, blocks);
      } else {
        blocks.add(child);
      }
    }
  }

  /** Whether {@code element} has a unit in it, at any depth. */
  static boolean holdsUnits(Element element) {
    return element.children().stream().anyMatch(child -> eId(child) != null || holdsUnits(child));
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
