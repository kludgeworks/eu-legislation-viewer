// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

/**
 * A text's units with an eId (articles, points, annexes…), in document order, each with the unit it
 * is in and its own blocks: its num, its heading and the blocks that aren't in a unit of its own,
 * even inside a list of its points (a list's opening words). A unit without an eId is part of the
 * one it is in. Where two units share an eId (as a converted text rarely has), the first is kept.
 *
 * @param byEId the units, by eId, in document order
 */
public record Units(SequencedMap<String, Unit> byEId) {

  /** The units of {@code text}. */
  public static Units of(Element text) {
    var units = new LinkedHashMap<String, Unit>();
    collect(text, null, units);
    return new Units(units);
  }

  /** Its unit {@code eId}; none if it has none. */
  public @Nullable Unit get(String eId) {
    return byEId.get(eId);
  }

  private static void collect(Element element, @Nullable String parent, Map<String, Unit> units) {
    var eId = eId(element);
    var inside = parent;
    if (eId != null && !units.containsKey(eId)) {
      var blocks = new ArrayList<Element>();
      own(element, blocks);
      units.put(eId, new Unit(eId, parent, blocks));
      inside = eId;
    }
    for (var child : element.children()) {
      collect(child, inside, units);
    }
  }

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

  private static boolean holdsUnits(Element element) {
    return element.children().stream().anyMatch(child -> eId(child) != null || holdsUnits(child));
  }

  /** A unit's eId: a unit's, not a note's or another element's. */
  private static @Nullable String eId(Element element) {
    return element.name().equals("unit") ? element.attribute("eId") : null;
  }

  /**
   * A unit.
   *
   * @param eId its eId
   * @param parent the eId of the unit it is in; none for an outermost unit
   * @param blocks its own blocks, in order
   */
  public record Unit(String eId, @Nullable String parent, List<Element> blocks) {
    public Unit {
      blocks = List.copyOf(blocks);
    }

    /** What each of its own blocks says. */
    public List<String> wording() {
      return blocks.stream().map(Wording::of).toList();
    }
  }
}
