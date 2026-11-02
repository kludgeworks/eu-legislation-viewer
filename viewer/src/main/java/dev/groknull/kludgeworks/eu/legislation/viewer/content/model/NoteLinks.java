// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.jspecify.annotations.Nullable;

/**
 * Footnote references, by what they cite rather than where: a noteRef cites its note by the note's
 * hash ({@code "note"}), not its href and marker, which the rebuilt text takes from the note (its
 * eId, its marker). So a block citing a note is the same in every version whose note says the same,
 * whatever the note is numbered; and a note that changes changes the blocks that cite it. Where
 * notes say the same, the hash leaves the href ambiguous: all but the first such note are cited
 * with a fact, {@code ["href", path, href]}. Notes cite notes, so theirs are hashed first, in the
 * order their citations need.
 */
final class NoteLinks {
  private static final String NOTE = "note";
  private static final String NOTE_REF = "noteRef";
  private static final String HREF = "href";
  private static final String MARKER = "marker";

  /** eId → hash of each note, as far as known. */
  private final Map<String, String> notes = new HashMap<>();

  /** Hashes more than one note has, and the eId of the first of them, by which they are cited. */
  private final Set<String> ambiguous = new HashSet<>();

  private final Map<String, String> first = new HashMap<>();

  private NoteLinks() {}

  /** The notes of a legal text, hashed. */
  static NoteLinks of(XdmNode root) {
    var links = new NoteLinks();
    var all = new ArrayList<XdmNode>();
    notes(root, all);
    var pending = new LinkedHashMap<String, List<Object>>();
    for (var note : all) {
      var eId = note.attribute(Annotations.EID);
      if (eId != null) {
        var json = JsonMl.of(note);
        Annotations.lift(json);
        pending.put(eId, json);
      }
    }
    while (!pending.isEmpty()) {
      var ready =
          pending.entrySet().stream()
              .filter(e -> cited(e.getValue()).stream().noneMatch(pending::containsKey))
              .toList();
      if (ready.isEmpty()) {
        throw new IllegalArgumentException("Notes cite each other in a cycle: " + pending.keySet());
      }
      for (var e : ready) {
        links.link(e.getValue());
        links.notes.put(e.getKey(), Identity.of(e.getValue()).hex());
      }
      ready.forEach(e -> pending.remove(e.getKey()));
    }
    var seen = new HashSet<String>();
    for (var note : all) {
      var eId = note.attribute(Annotations.EID);
      var hash = eId == null ? null : links.notes.get(eId);
      if (hash != null) {
        if (!seen.add(hash)) {
          links.ambiguous.add(hash);
        }
        links.first.putIfAbsent(hash, eId);
      }
    }
    return links;
  }

  /** Cites the notes a block refers to by their hashes; the facts for ambiguous ones. */
  List<Object> link(List<?> leaf) {
    var facts = new ArrayList<Object>();
    link(leaf, List.of(), facts);
    return facts;
  }

  private void link(List<?> el, List<Integer> path, List<Object> facts) {
    var attrs = JsonMl.attributes(el);
    if (JsonMl.name(el).equals(NOTE_REF) && attrs.get(HREF) instanceof String href) {
      var hash = notes.get(target(href));
      if (hash != null) {
        attrs.remove(HREF);
        attrs.remove(MARKER); // the note's own
        attrs.put(NOTE, hash);
        if (ambiguous.contains(hash) && !target(href).equals(first.get(hash))) {
          facts.add(List.of(HREF, path, href));
        }
      }
    }
    var children = JsonMl.elements(el);
    for (var i = 0; i < children.size(); i++) {
      var child = new ArrayList<>(path);
      child.add(i);
      link(children.get(i), List.copyOf(child), facts);
    }
  }

  private static List<String> cited(List<?> el) {
    var out = new ArrayList<String>();
    if (JsonMl.name(el).equals(NOTE_REF) && JsonMl.attributes(el).get(HREF) instanceof String h) {
      out.add(target(h));
    }
    JsonMl.elements(el).forEach(c -> out.addAll(cited(c)));
    return out;
  }

  private static String target(String href) {
    return href.startsWith("#") ? href.substring(1) : href;
  }

  private static void notes(XdmNode node, List<XdmNode> out) {
    for (var child : node.children()) {
      if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
        if (new QName(LegalText.NAMESPACE, NOTE).equals(child.getNodeName())) {
          out.add(child);
        }
        notes(child, out);
      }
    }
  }

  /**
   * The notes of a text being rebuilt, read from its blocks as stored, in document order: each
   * note's eId and marker by its hash, to put back the href and marker of the references citing it.
   */
  static final class Targets {
    private record Note(@Nullable Object eId, @Nullable Object marker) {}

    private final Map<String, Note> notes = new HashMap<>();

    /** Reads the notes in a block as stored, with its facts. */
    void read(List<?> stored, List<?> facts) {
      read(stored, List.of(), facts);
    }

    private void read(List<?> el, List<Integer> path, List<?> facts) {
      if (JsonMl.name(el).equals(NOTE)) {
        var hash = Identity.of(el).hex();
        notes.putIfAbsent(
            hash, new Note(fact(facts, Annotations.EID, path), JsonMl.attributes(el).get(MARKER)));
      }
      var children = JsonMl.elements(el);
      for (var i = 0; i < children.size(); i++) {
        var child = new ArrayList<>(path);
        child.add(i);
        read(children.get(i), List.copyOf(child), facts);
      }
    }

    /** Puts back the href and marker of each reference in a block citing a note by its hash. */
    void unlink(List<?> leaf, List<?> facts) {
      unlink(leaf, List.of(), facts);
    }

    private void unlink(List<?> el, List<Integer> path, List<?> facts) {
      var attrs = JsonMl.attributes(el);
      if (JsonMl.name(el).equals(NOTE_REF) && attrs.remove(NOTE) instanceof String hash) {
        var note = notes.get(hash);
        if (note == null || note.eId() == null) {
          throw new IllegalStateException("A reference to a note not in the text: " + hash);
        }
        var href = fact(facts, HREF, path);
        attrs.put(HREF, href != null ? href : "#" + note.eId());
        if (note.marker() != null) {
          attrs.put(MARKER, note.marker());
        }
      }
      var children = JsonMl.elements(el);
      for (var i = 0; i < children.size(); i++) {
        var child = new ArrayList<>(path);
        child.add(i);
        unlink(children.get(i), List.copyOf(child), facts);
      }
    }

    private static @Nullable Object fact(List<?> facts, String kind, List<Integer> path) {
      for (var fact : facts) {
        var f = (List<?>) fact;
        if (f.getFirst().equals(kind)
            && ((List<?>) f.get(1))
                .stream().map(i -> ((Number) i).intValue()).toList().equals(path)) {
          return f.get(2);
        }
      }
      return null;
    }
  }
}
