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
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The occurrences of a text that have an eId, in document order. Examples are articles, points and
 * annexes.
 *
 * <p>Each occurrence holds the occurrence that it is in, the occurrences in it and its own blocks.
 * Its own blocks are its num, its heading and the blocks that are not in an occurrence of their
 * own. This is true even inside a list of its points, for example the opening words of a list. An
 * occurrence without an eId is part of the occurrence that it is in. If two occurrences have the
 * same eId, the first is kept. A converted text rarely has this. A table is one block of the
 * occurrence that it is in, with the occurrences in its cells.
 *
 * <p>What a consolidation keeps of a deletion is not part of the text. This is the legal-text
 * {@code deleted}: the struck words or occurrences that an act deleted, under its marker. An
 * occurrence in it is not one of the occurrences, and its words are not in the blocks of an
 * occurrence. The act that struck each occurrence is kept.
 *
 * <p>An occurrence holds the notes that its own blocks refer to. These are the notes of the text,
 * at its end. A note is matched through the reference to it, not by its eId. The eId of a note is
 * its place, note_N, and it changes when a note before it comes or goes. The notes of a table are
 * in the table, and those of a file of an act as published are in its occurrence. They are compared
 * where they are, as blocks.
 *
 * @param byEId the occurrences, by eId, in document order
 * @param outermost the eIds of the occurrences that are in no other occurrence, in order
 * @param struck the markers of the deletions that keep an occurrence struck, by the eId of the
 *     occurrence. For example, "deleted by 2019/103".
 * @param notes the notes of the text, at its end, by eId
 */
public record Occurrences(
    SequencedMap<String, Occurrence> byEId,
    List<String> outermost,
    Map<String, List<Marker>> struck,
    Map<String, Element> notes) {

  /**
   * The elements of legal-text that hold only running text or MathML: a paragraph, a num, a formula
   * and the inline elements. Legal-text has no notes in them.
   */
  private static final Set<String> RUNNING =
      Set.of(
          "p",
          "num",
          "math",
          "t",
          "emphasis",
          "date",
          "number",
          "quote",
          "noteRef",
          "ref",
          "eol",
          "span",
          "img",
          "changed",
          "docType",
          "docNumber",
          "docDate",
          "docPurpose");

  /** Returns the occurrences of {@code text}. */
  public static Occurrences of(Element text) {
    var occurrences = new LinkedHashMap<String, Occurrence>();
    var outermost = new ArrayList<String>();
    var struck = new HashMap<String, List<Marker>>();
    var notes = notes(text);
    collect(text, null, null, outermost, occurrences, new ArrayDeque<>(), struck, notes);
    return new Occurrences(occurrences, outermost, struck, notes);
  }

  /**
   * Returns the notes of the text that {@code element} refers to, at any depth, in the order of
   * their first reference.
   */
  public List<Element> notesOf(Element element) {
    var found = new LinkedHashMap<String, Element>();
    referred(element, notes, found);
    return List.copyOf(found.values());
  }

  /**
   * Returns the notes of {@code text}, by eId. A consolidation has them at its end. An act as the
   * Official Journal published it may have them at the end of an annex instead.
   */
  private static Map<String, Element> notes(Element text) {
    var notes = new HashMap<String, Element>();
    collectNotes(text, notes);
    return notes;
  }

  /**
   * Adds the notes of each {@code notes} in {@code element} to {@code notes}. A text has them at
   * its end, at the end of an occurrence or a file of an annex, and under a table, wherever the
   * table is. The search does not go into what holds only running text or MathML, which cannot have
   * them.
   */
  private static void collectNotes(Element element, Map<String, Element> notes) {
    for (var child : element.children()) {
      if (RUNNING.contains(child.name())) {
        continue;
      }
      if (!child.name().equals("notes")) {
        collectNotes(child, notes);
        continue;
      }
      for (var note : child.children()) {
        var eId = note.attribute("eId");
        if (note.name().equals("note") && eId != null) {
          notes.put(eId, note);
        }
      }
    }
  }

  /**
   * Adds the notes of {@code notes} that {@code element} refers to, to {@code found}, by eId. What
   * a consolidation keeps of a deletion is not part of the text, nor are its notes.
   */
  private static void referred(
      Element element, Map<String, Element> notes, Map<String, Element> found) {
    if (element.name().equals("noteRef")) {
      var href = element.attribute("href");
      var note = href == null || !href.startsWith("#") ? null : notes.get(href.substring(1));
      if (note != null) {
        found.putIfAbsent(href, note);
      }
      return;
    }
    if (element.name().equals("deleted")) {
      return;
    }
    for (var child : element.children()) {
      referred(child, notes, found);
    }
  }

  /** Returns the occurrence {@code eId}. Returns null if there is no such occurrence. */
  public @Nullable Occurrence get(String eId) {
    return byEId.get(eId);
  }

  /**
   * Returns the eIds of the occurrences directly in the occurrence {@code parent}. If {@code
   * parent} is null, returns the eIds of the outermost occurrences.
   */
  public List<String> in(@Nullable String parent) {
    if (parent == null) {
      return outermost;
    }
    var occurrence = get(parent);
    return occurrence == null ? List.of() : occurrence.occurrences();
  }

  private static void collect(
      Element element,
      @Nullable String parent,
      @Nullable Element container,
      List<String> siblings,
      Map<String, Occurrence> occurrences,
      Deque<Ranked> ranked,
      Map<String, List<Marker>> struck,
      Map<String, Element> notes) {
    if (element.name().equals("deleted")) {
      struck(element, markers(element), struck);
      return;
    }
    if (block(element)) {
      return;
    }
    var eId = eId(element);
    if (eId != null && !occurrences.containsKey(eId)) {
      var blocks = new ArrayList<Element>();
      own(element, blocks);
      var section = section(eId, element, parent, occurrences, ranked);
      var list = list(container);
      var referred = new LinkedHashMap<String, Element>();
      blocks.forEach(block -> referred(block, notes, referred));
      var ownNotes = List.copyOf(referred.values());
      // Put the occurrence now, so that it comes before the occurrences in it in document order.
      // Put it again
      // in the same place when the occurrences in it are known.
      occurrences.put(
          eId, new Occurrence(eId, parent, section, element, blocks, ownNotes, List.of(), list));
      siblings.add(eId);
      var inside = new ArrayList<String>();
      for (var child : element.children()) {
        collect(child, eId, element, inside, occurrences, ranked, struck, notes);
      }
      occurrences.put(
          eId, new Occurrence(eId, parent, section, element, blocks, ownNotes, inside, list));
      return;
    }
    for (var child : element.children()) {
      collect(child, parent, element, siblings, occurrences, ranked, struck, notes);
    }
  }

  /**
   * Returns the section of the occurrence {@code eId}. If the occurrence has a rank, the section is
   * the occurrence itself. It goes under the last open occurrence ranked above it, as {@code
   * Outlines} nests them. If the occurrence has no rank, the section is the last open ranked
   * occurrence whose parent also encloses this occurrence.
   */
  private static @Nullable String section(
      String eId,
      Element element,
      @Nullable String parent,
      Map<String, Occurrence> occurrences,
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
    for (var at = parent; at != null; at = Objects.requireNonNull(occurrences.get(at)).parent()) {
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

  /** Records the outermost occurrences in {@code element} as struck by {@code markers}. */
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

  /** An occurrence with a rank that is still open for the occurrences after it. */
  private record Ranked(String eId, int rank, @Nullable String parent) {}

  /**
   * Adds the blocks of {@code element} that are not in an occurrence of their own to {@code
   * blocks}. The notes at the end of an annex are not its blocks: each belongs to the occurrence
   * that refers to it.
   */
  private static void own(Element element, List<Element> blocks) {
    for (var child : element.children()) {
      if (eId(child) != null
          || child.name().equals("provenance")
          || child.name().equals("deleted")
          || child.name().equals("notes")) {
        continue;
      }
      if (holdsOccurrences(child)) {
        own(child, blocks);
      } else {
        blocks.add(child);
      }
    }
  }

  /**
   * Returns whether {@code element} has an occurrence of the text in it, at any depth. A struck
   * occurrence does not count.
   */
  static boolean holdsOccurrences(Element element) {
    return !block(element)
        && element.children().stream()
            .filter(child -> !child.name().equals("deleted"))
            .anyMatch(child -> eId(child) != null || holdsOccurrences(child));
  }

  /**
   * Returns whether {@code element} is one block, with the occurrences in it. A table is one block.
   * The converter numbers the lists in its cells through the document, so one list inserted in a
   * cell renumbers the occurrences of every list after it. For example,
   * tab_1/tr_14/td_2/list_2/idt_1 becomes list_3/idt_1. {@link Marks} compares a table by its rows
   * and cells instead.
   */
  static boolean block(Element element) {
    return element.name().equals("table");
  }

  /** Returns the eId of an occurrence. Returns null for any other element, for example a note. */
  static @Nullable String eId(Element element) {
    return element.name().equals("occurrence") ? element.attribute("eId") : null;
  }

  /**
   * An occurrence.
   *
   * @param eId its eId
   * @param parent the eId of the occurrence that it is in. Null for an outermost occurrence.
   * @param section the eId of the occurrence with a rank that it is in, as an outline nests them.
   *     It is the occurrence itself if the occurrence has a rank. Null if it is in no such
   *     occurrence.
   * @param element the occurrence as the text has it, with the occurrences in it
   * @param blocks its own blocks, in order
   * @param notes the notes of the text that its own blocks refer to, in the order of their first
   *     reference
   * @param occurrences the eIds of the occurrences directly in it, in order
   * @param list the list that it is an item of, without its items. The list keeps its kind, for
   *     example alpha or dash. Null if it is in no list.
   */
  public record Occurrence(
      String eId,
      @Nullable String parent,
      @Nullable String section,
      Element element,
      List<Element> blocks,
      List<Element> notes,
      List<String> occurrences,
      @Nullable Element list) {
    public Occurrence {
      blocks = List.copyOf(blocks);
      notes = List.copyOf(notes);
      occurrences = List.copyOf(occurrences);
    }

    /** Returns what a comparison matches each of its own blocks on. See {@link Wording#key}. */
    public List<String> keys() {
      return blocks.stream().map(Wording::key).toList();
    }
  }
}
