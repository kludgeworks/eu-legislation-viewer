// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Makes the outline of a text, when the text is stored.
 *
 * <p>The outline holds the units that have a rank, in the order in which a reader meets them. Each
 * unit is under the last unit of a higher rank before it. The outline also holds the eId of every
 * container: an element that holds elements and is not a block. The labels and headings are read as
 * plain text, without the notes that they cite and without the text that an amendment deleted.
 */
public final class Outlines {

  /**
   * The blocks: what a reader reads and what an amendment replaces. They are not containers, and
   * the outline does not look into them.
   */
  private static final Set<String> BLOCKS =
      Set.of("p", "heading", "tr", "note", "longTitle", "num", "docTitle");

  /** What the text of a title leaves out: note references, deleted text, images and formulas. */
  private static final Set<String> UNREAD = Set.of("noteRef", "deleted", "img", "m:math");

  private final List<Unit> outermost = new ArrayList<>();
  private final List<String> eIds = new ArrayList<>();
  private final Deque<Unit> open = new ArrayDeque<>();

  private Outlines() {}

  /**
   * @param text the element of a text, {@code legalText}
   */
  public static Outline of(Element text) {
    var outlines = new Outlines();
    outlines.walk(text);
    return new Outline(outlines.outermost.stream().map(Unit::entry).toList(), outlines.eIds);
  }

  /** Walks a container and the containers in it, in order. */
  private void walk(Element container) {
    var eId = container.attribute("eId");
    if (eId != null) {
      eIds.add(eId);
    }
    var rank = container.attribute("rank");
    if (container.name().equals("unit") && rank != null) {
      add(container, Integer.parseInt(rank));
    }
    for (var child : container.children()) {
      if (!BLOCKS.contains(child.name()) && !child.children().isEmpty()) {
        walk(child);
      }
    }
  }

  /** Adds a unit with a title, under the last open unit that is ranked above it. */
  private void add(Element element, int rank) {
    var unit =
        new Unit(
            element.attributes().getOrDefault("eId", ""),
            Objects.requireNonNullElse(element.attribute("kind"), ""),
            title(element, "num"),
            title(element, "heading"),
            rank);
    while (!open.isEmpty() && open.peek().rank >= rank) {
      open.pop();
    }
    (open.isEmpty() ? outermost : open.peek().under).add(unit);
    open.push(unit);
  }

  /**
   * Returns the text of the child {@code name} of the unit, for example its num or its heading.
   * Returns an empty string if the unit has no such child.
   */
  private static String title(Element unit, String name) {
    for (var child : unit.children()) {
      if (child.name().equals(name)) {
        var text = new StringBuilder();
        read(child, text);
        return text.toString().replaceAll("\\s+", " ").strip();
      }
    }
    return "";
  }

  private static void read(Element element, StringBuilder text) {
    var name = element.name();
    if (UNREAD.contains(name)) {
      return;
    }
    var value = element.attribute("v");
    if (name.equals("eol")) {
      text.append(' ');
    } else if (value != null) {
      text.append(value);
    }
    element.children().forEach(child -> read(child, text));
  }

  /** A unit in the outline while it is read. The units under it are still being added. */
  private static final class Unit {
    final String eId;
    final String kind;
    final String num;
    final String heading;
    final int rank;
    final List<Unit> under = new ArrayList<>();

    Unit(String eId, String kind, String num, String heading, int rank) {
      this.eId = eId;
      this.kind = kind;
      this.num = num;
      this.heading = heading;
      this.rank = rank;
    }

    Outline.Entry entry() {
      return new Outline.Entry(eId, kind, num, heading, under.stream().map(Unit::entry).toList());
    }
  }
}
