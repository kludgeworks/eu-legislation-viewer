// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Writes the edits of an element as runs: each run of inserted words in {@code diff:ins}, and each
 * run of deleted words in {@code diff:del}, where it stood. It is a walk of the tokens of the
 * element ({@link Wording}). It wraps the tokens in {@code edited} in {@code edit}. It puts the
 * words deleted before a token, from {@code deletedAt}, in {@code diff:del}.
 */
final class Runs implements Wording.Sink {
  private final String edit;
  private final boolean[] edited;
  private final Map<Integer, List<Element>> deletedAt;
  private int next;

  private Runs(String edit, boolean[] edited, Map<Integer, List<Element>> deletedAt) {
    this.edit = edit;
    this.edited = edited;
    this.deletedAt = deletedAt;
  }

  @Override
  public List<Element> words(Element t, List<String> words) {
    if (words.isEmpty()) {
      return List.of(t);
    }
    var out = new ArrayList<Element>();
    var run = new StringBuilder();
    var runEdited = false;
    for (var word : words) {
      var deleted = deletedAt.get(next);
      if (deleted != null || edited[next] != runEdited) {
        flush(t, run, runEdited, out);
        runEdited = edited[next];
      }
      if (deleted != null) {
        out.add(wrap(Edits.DELETED, deleted));
      }
      run.append(word);
      next++;
    }
    flush(t, run, runEdited, out);
    return out;
  }

  @Override
  public List<Element> token(Element element, String said, boolean editable) {
    var out = deletedBefore();
    out.add(editable && edited[next] ? wrap(edit, element) : element);
    next++;
    return out;
  }

  @Override
  public Wording.QuotationMark quotationMark(String mark) {
    if (mark.isEmpty()) {
      return Wording.QuotationMark.NONE;
    }
    var before = deletedBefore();
    var shown = edited[next] ? wrap(edit, text(null, mark)) : null;
    next++;
    return new Wording.QuotationMark(before, shown);
  }

  /** Returns the words deleted after the last token. They go at the end of the edited element. */
  List<Element> deleted() {
    var deleted = deletedAt.get(next);
    return deleted == null ? List.of() : List.of(wrap(Edits.DELETED, deleted));
  }

  private List<Element> deletedBefore() {
    var out = new ArrayList<Element>();
    var deleted = deletedAt.get(next);
    if (deleted != null) {
      out.add(wrap(Edits.DELETED, deleted));
    }
    return out;
  }

  private void flush(Element t, StringBuilder run, boolean runEdited, List<Element> out) {
    if (run.isEmpty()) {
      return;
    }
    var piece = text(t, run.toString());
    out.add(runEdited ? wrap(edit, piece) : piece);
    run.setLength(0);
  }

  /**
   * Returns a {@code t} that says {@code v}, with the other attributes of {@code like}. If {@code
   * like} is null, it has no other attributes.
   */
  static Element text(@Nullable Element like, String v) {
    var attributes = new HashMap<String, String>(like == null ? Map.of() : like.attributes());
    attributes.put("v", v);
    return new Element("t", attributes, List.of(), "");
  }

  private static Element wrap(String edit, Element element) {
    return new Element(edit, Map.of(), List.of(element), "");
  }

  /**
   * Returns {@code pieces} in {@code edit}. The words of a run of {@code t} with the same other
   * attributes join into one {@code t}.
   */
  private static Element wrap(String edit, List<Element> pieces) {
    var joined = new ArrayList<Element>();
    for (var piece : pieces) {
      var last = joined.isEmpty() ? null : joined.getLast();
      if (last != null
          && last.name().equals("t")
          && piece.name().equals("t")
          && others(last).equals(others(piece))) {
        joined.set(joined.size() - 1, text(last, last.attribute("v") + piece.attribute("v")));
      } else {
        joined.add(piece);
      }
    }
    return new Element(edit, Map.of(), joined, "");
  }

  /** Returns the attributes of {@code t} other than its words. */
  private static Map<String, String> others(Element t) {
    var others = new HashMap<>(t.attributes());
    others.remove("v");
    return others;
  }

  /**
   * Returns {@code element} with the tokens in {@code edited} wrapped in {@code edit}, and the
   * deleted words added.
   */
  static Element edited(
      Element element, String edit, boolean[] edited, Map<Integer, List<Element>> deletedAt) {
    var editing = new Runs(edit, edited, deletedAt);
    var walked = Wording.walk(element, editing);
    // The element is the last in its place. A walk can put something before it, for example the
    // words deleted before the opening quotation mark of a quote. Keep these in the element.
    var shown = walked.getLast();
    var children = new ArrayList<>(walked.subList(0, walked.size() - 1));
    children.addAll(shown.children());
    children.addAll(editing.deleted());
    return Differ.copy(shown, children);
  }
}
