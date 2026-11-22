// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
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
 * {@code deleted}: the struck words or occurrences that an act deleted, under its tag. An
 * occurrence in it is not one of the occurrences, and its words are not in the blocks of an
 * occurrence. The act that struck each occurrence is kept.
 *
 * <p>An occurrence holds the footnotes that its own blocks refer to. These are the footnotes of the
 * text, at its end. A footnote is matched through the reference to it, not by its eId. The eId of a
 * footnote is its place, note_N, and it changes when a footnote before it comes or goes. The
 * footnotes of a table are in the table, and those of a file of an act as published are in its
 * occurrence. They are compared where they are, as blocks.
 *
 * @param byEId the occurrences, by eId, in document order
 * @param outermost the eIds of the occurrences that are in no other occurrence, in order
 * @param struck the tags of the deletions that keep an occurrence struck, by the eId of the
 *     occurrence. For example, "deleted by 2019/103".
 * @param footnotes the footnotes of the text, at its end, by eId
 */
public record Occurrences(
    SequencedMap<String, Occurrence> byEId,
    List<String> outermost,
    Map<String, List<Tag>> struck,
    Map<String, Element> footnotes) {

  /**
   * The elements of legal-text that hold only running text or MathML: a paragraph, a num, a formula
   * and the inline elements. Legal-text has no footnotes in them.
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
          "footnoteRef",
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
    var struck = new HashMap<String, List<Tag>>();
    var footnotes = footnotes(text);
    collect(text, null, null, outermost, occurrences, new ArrayDeque<>(), struck, footnotes);
    return new Occurrences(occurrences, outermost, struck, footnotes);
  }

  /**
   * Returns the footnotes of the text that {@code element} refers to, at any depth, in the order of
   * their first reference.
   */
  public List<Element> footnotesOf(Element element) {
    var found = new LinkedHashMap<String, Element>();
    referred(element, footnotes, found);
    return List.copyOf(found.values());
  }

  /**
   * Returns the footnotes of {@code text}, by eId. A consolidation has them at its end. An act as
   * the Official Journal published it may have them at the end of an annex instead.
   */
  private static Map<String, Element> footnotes(Element text) {
    var footnotes = new HashMap<String, Element>();
    collectFootnotes(text, footnotes);
    return footnotes;
  }

  /**
   * Adds the footnotes of each {@code footnotes} in {@code element} to {@code footnotes}. A text
   * has them at its end, at the end of an occurrence or a file of an annex, and under a table,
   * wherever the table is. The search does not go into what holds only running text or MathML,
   * which cannot have them.
   */
  private static void collectFootnotes(Element element, Map<String, Element> footnotes) {
    for (var child : element.children()) {
      if (RUNNING.contains(child.name())) {
        continue;
      }
      if (!child.name().equals("footnotes")) {
        collectFootnotes(child, footnotes);
        continue;
      }
      for (var footnote : child.children()) {
        var eId = footnote.attribute("eId");
        if (footnote.name().equals("footnote") && eId != null) {
          footnotes.put(eId, footnote);
        }
      }
    }
  }

  /**
   * Adds the footnotes of {@code footnotes} that {@code element} refers to, to {@code found}, by
   * eId. What a consolidation keeps of a deletion is not part of the text, nor are its footnotes.
   */
  private static void referred(
      Element element, Map<String, Element> footnotes, Map<String, Element> found) {
    if (element.name().equals("footnoteRef")) {
      var href = element.attribute("href");
      var footnote =
          href == null || !href.startsWith("#") ? null : footnotes.get(href.substring(1));
      if (footnote != null) {
        found.putIfAbsent(href, footnote);
      }
      return;
    }
    if (element.name().equals("deleted")) {
      return;
    }
    for (var child : element.children()) {
      referred(child, footnotes, found);
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
      Map<String, List<Tag>> struck,
      Map<String, Element> footnotes) {
    if (element.name().equals("deleted")) {
      struck(element, tags(element), struck);
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
      blocks.forEach(block -> referred(block, footnotes, referred));
      var ownFootnotes = List.copyOf(referred.values());
      // Put the occurrence now, so that it comes before the occurrences in it in document order.
      // Put it again
      // in the same place when the occurrences in it are known.
      occurrences.put(
          eId,
          new Occurrence(eId, parent, section, element, blocks, ownFootnotes, List.of(), list));
      siblings.add(eId);
      var inside = new ArrayList<String>();
      for (var child : element.children()) {
        collect(child, eId, element, inside, occurrences, ranked, struck, footnotes);
      }
      occurrences.put(
          eId, new Occurrence(eId, parent, section, element, blocks, ownFootnotes, inside, list));
      return;
    }
    for (var child : element.children()) {
      collect(child, parent, element, siblings, occurrences, ranked, struck, footnotes);
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
   * Returns the deletion tags among the children of {@code deletion}. For example, "deleted by
   * 2019/103".
   */
  private static List<Tag> tags(Element deletion) {
    var tags = new ArrayList<Tag>();
    for (var child : deletion.children()) {
      var act = child.attribute("amendingAct");
      var action = child.attribute("action");
      if (child.name().equals("tag") && act != null && action != null) {
        tags.add(new Tag(act, action, null));
      }
    }
    return List.copyOf(tags);
  }

  /** Records the outermost occurrences in {@code element} as struck by {@code tags}. */
  private static void struck(Element element, List<Tag> tags, Map<String, List<Tag>> struck) {
    for (var child : element.children()) {
      var eId = eId(child);
      if (eId != null) {
        struck.putIfAbsent(eId, tags);
      } else {
        struck(child, tags, struck);
      }
    }
  }

  /** An occurrence with a rank that is still open for the occurrences after it. */
  private record Ranked(String eId, int rank, @Nullable String parent) {}

  /**
   * Adds the blocks of {@code element} that are not in an occurrence of their own to {@code
   * blocks}. The footnotes at the end of an annex are not its blocks: each belongs to the
   * occurrence that refers to it.
   */
  private static void own(Element element, List<Element> blocks) {
    for (var child : element.children()) {
      if (eId(child) != null
          || child.name().equals("tag")
          || child.name().equals("deleted")
          || child.name().equals("footnotes")) {
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
   * tab_1/tr_14/td_2/list_2/idt_1 becomes list_3/idt_1. {@link Edits} compares a table by its rows
   * and cells instead.
   */
  static boolean block(Element element) {
    return element.name().equals("table");
  }

  /**
   * Returns the eId of an occurrence. Returns null for any other element, for example a footnote.
   */
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
   * @param footnotes the footnotes of the text that its own blocks refer to, in the order of their
   *     first reference
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
      List<Element> footnotes,
      List<String> occurrences,
      @Nullable Element list) {
    public Occurrence {
      blocks = List.copyOf(blocks);
      footnotes = List.copyOf(footnotes);
      occurrences = List.copyOf(occurrences);
    }

    /** Returns what a comparison matches each of its own blocks on. See {@link Wording#key}. */
    public List<String> keys() {
      return blocks.stream().map(Wording::key).toList();
    }
  }
}
