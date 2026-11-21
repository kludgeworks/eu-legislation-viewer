// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Makes the outline of a text, when the text is stored.
 *
 * <p>The outline holds the occurrences that have a rank, in the order in which a reader meets them.
 * Each occurrence is under the last occurrence of a higher rank before it. The outline also holds
 * the eId of every container: an element that holds elements and is not a block. The labels and
 * headings are read as plain text, without the notes that they cite and without the text that an
 * amendment deleted.
 *
 * <p>It also holds the tags of each occurrence: the acts that its {@code tag} elements name, and
 * the acts that its {@code changed} elements name. A {@code changed} element tags words that an act
 * changed inside a sentence. For example, in Article 15(1) of 767/2008 of 18 October 2013, "or
 * extend the visa" is tagged with 2009/810. A tag in a block that has no eId, such as a paragraph
 * or a deleted text, tags the nearest occurrence around it.
 *
 * <p>{@link OutlinesVersion} is the version of these rules. A kept outline that other rules made is
 * made again.
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

  private final List<Occurrence> outermost = new ArrayList<>();
  private final List<String> eIds = new ArrayList<>();
  private final Map<String, Set<String>> tags = new LinkedHashMap<>();
  private final Deque<Occurrence> open = new ArrayDeque<>();

  private Outlines() {}

  /**
   * @param text the element of a text, {@code legalText}
   */
  public static Outline of(Element text) {
    var outlines = new Outlines();
    outlines.walk(text);
    outlines.tag(text, "");
    return new Outline(
        outlines.outermost.stream().map(Occurrence::entry).toList(), outlines.eIds, outlines.tags);
  }

  /**
   * Adds the acts that the {@code tag} and {@code changed} elements in {@code element} name to the
   * tags of their occurrence. {@code occurrence} is the eId of the nearest occurrence around {@code
   * element}, or empty for none.
   */
  private void tag(Element element, String occurrence) {
    var eId = Objects.requireNonNullElse(element.attribute("eId"), occurrence);
    if (element.name().equals("tag") || element.name().equals("changed")) {
      var act = element.attribute("amendingAct");
      if (act != null && !eId.isEmpty()) {
        tags.computeIfAbsent(eId, each -> new TreeSet<>()).add(act);
      }
    }
    if (!element.name().equals("tag")) {
      element.children().forEach(child -> tag(child, eId));
    }
  }

  /** Walks a container and the containers in it, in order. */
  private void walk(Element container) {
    var eId = container.attribute("eId");
    if (eId != null) {
      eIds.add(eId);
    }
    var rank = container.attribute("rank");
    if (container.name().equals("occurrence") && rank != null) {
      add(container, Integer.parseInt(rank));
    }
    for (var child : container.children()) {
      if (!BLOCKS.contains(child.name()) && !child.children().isEmpty()) {
        walk(child);
      }
    }
  }

  /** Adds an occurrence with a title, under the last open occurrence that is ranked above it. */
  private void add(Element element, int rank) {
    var occurrence =
        new Occurrence(
            element.attributes().getOrDefault("eId", ""),
            Objects.requireNonNullElse(element.attribute("kind"), ""),
            title(element, "num"),
            title(element, "heading"),
            rank);
    while (!open.isEmpty() && open.peek().rank >= rank) {
      open.pop();
    }
    (open.isEmpty() ? outermost : open.peek().under).add(occurrence);
    open.push(occurrence);
  }

  /**
   * Returns the text of the child {@code name} of the occurrence, for example its num or its
   * heading. Returns an empty string if the occurrence has no such child.
   */
  private static String title(Element occurrence, String name) {
    for (var child : occurrence.children()) {
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

  /**
   * An occurrence in the outline while it is read. The occurrences under it are still being added.
   */
  private static final class Occurrence {
    final String eId;
    final String kind;
    final String num;
    final String heading;
    final int rank;
    final List<Occurrence> under = new ArrayList<>();

    Occurrence(String eId, String kind, String num, String heading, int rank) {
      this.eId = eId;
      this.kind = kind;
      this.num = num;
      this.heading = heading;
      this.rank = rank;
    }

    Outline.Entry entry() {
      return new Outline.Entry(
          eId, kind, num, heading, under.stream().map(Occurrence::entry).toList());
    }
  }
}
