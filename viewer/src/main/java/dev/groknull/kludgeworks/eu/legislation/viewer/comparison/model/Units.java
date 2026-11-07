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
 * The units of a text that have an eId, in document order. Examples are articles, points and
 * annexes.
 *
 * <p>Each unit holds the unit that it is in, the units in it and its own blocks. Its own blocks are
 * its num, its heading and the blocks that are not in a unit of their own. This is true even inside
 * a list of its points, for example the opening words of a list. A unit without an eId is part of
 * the unit that it is in. If two units have the same eId, the first is kept. A converted text
 * rarely has this.
 *
 * <p>What a consolidation keeps of a deletion is not part of the text. This is the legal-text
 * {@code deleted}: the struck words or units that an act deleted, under its marker. A unit in it is
 * not one of the units, and its words are not in the blocks of a unit. The act that struck each
 * unit is kept.
 *
 * @param byEId the units, by eId, in document order
 * @param outermost the eIds of the units that are in no other unit, in order
 * @param struck the markers of the deletions that keep a unit struck, by the eId of the unit. For
 *     example, "deleted by 2019/103".
 */
public record Units(
    SequencedMap<String, Unit> byEId, List<String> outermost, Map<String, List<Marker>> struck) {

  /** Returns the units of {@code text}. */
  public static Units of(Element text) {
    var units = new LinkedHashMap<String, Unit>();
    var outermost = new ArrayList<String>();
    var struck = new HashMap<String, List<Marker>>();
    collect(text, null, null, outermost, units, new ArrayDeque<>(), struck);
    return new Units(units, outermost, struck);
  }

  /** Returns the unit {@code eId}. Returns null if there is no such unit. */
  public @Nullable Unit get(String eId) {
    return byEId.get(eId);
  }

  /**
   * Returns the eIds of the units directly in the unit {@code parent}. If {@code parent} is null,
   * returns the eIds of the outermost units.
   */
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
      // Put the unit now, so that it comes before the units in it in document order. Put it again
      // in the same place when the units in it are known.
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
   * Returns the section of the unit {@code eId}. If the unit has a rank, the section is the unit
   * itself. It goes under the last open unit ranked above it, as {@code Outlines} nests them. If
   * the unit has no rank, the section is the last open ranked unit whose parent also encloses this
   * unit.
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

  /** Returns {@code container} without its items, if it is a list. Returns null if it is not. */
  private static @Nullable Element list(@Nullable Element container) {
    return container == null || !container.name().equals("list")
        ? null
        : new Element(container.name(), container.attributes(), List.of(), container.text());
  }

  /**
   * Returns the deletion markers among the children of {@code deletion}. For example, "deleted by
   * 2019/103".
   */
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

  /** Records the outermost units in {@code element} as struck by {@code markers}. */
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

  /** A unit with a rank that is still open for the units after it. */
  private record Ranked(String eId, int rank, @Nullable String parent) {}

  /** Adds the blocks of {@code element} that are not in a unit of their own to {@code blocks}. */
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

  /**
   * Returns whether {@code element} has a unit of the text in it, at any depth. A struck unit does
   * not count.
   */
  static boolean holdsUnits(Element element) {
    return element.children().stream()
        .filter(child -> !child.name().equals("deleted"))
        .anyMatch(child -> eId(child) != null || holdsUnits(child));
  }

  /** Returns the eId of a unit. Returns null for any other element, for example a note. */
  static @Nullable String eId(Element element) {
    return element.name().equals("unit") ? element.attribute("eId") : null;
  }

  /**
   * A unit.
   *
   * @param eId its eId
   * @param parent the eId of the unit that it is in. Null for an outermost unit.
   * @param section the eId of the unit with a rank that it is in, as an outline nests them. It is
   *     the unit itself if the unit has a rank. Null if it is in no such unit.
   * @param element the unit as the text has it, with the units in it
   * @param blocks its own blocks, in order
   * @param units the eIds of the units directly in it, in order
   * @param list the list that it is an item of, without its items. The list keeps its kind, for
   *     example alpha or dash. Null if it is in no list.
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

    /** Returns what each of its own blocks says. */
    public List<String> wording() {
      return blocks.stream().map(Wording::of).toList();
    }
  }
}
