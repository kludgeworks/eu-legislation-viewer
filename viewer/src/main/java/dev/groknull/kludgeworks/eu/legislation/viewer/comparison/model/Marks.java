// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiffWithLinearSpace;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.deltamerge.InlineDeltaMergeInfo;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Marks words in legal-text, as a comparison shows them. Inserted words go in {@code diff:ins}, and
 * deleted words go in {@code diff:del}. The marks are only around text, a formula or an image, that
 * is the words of a {@code t}, a {@code number}, a {@code math} or an {@code img}. Thus an element
 * keeps the parent that it has in the text.
 *
 * <p>A block is read as tokens, in the order in which {@link Wording} reads it:
 *
 * <ul>
 *   <li>the words, spaces and punctuation of a {@code t}, from {@link Wording#words}
 *   <li>a whole {@code number}
 *   <li>an {@code eol}
 *   <li>an image
 *   <li>the marks of a quote
 *   <li>a whole formula
 * </ul>
 *
 * <p>The tokens of a {@code t}, a {@code number}, a formula and an image are marked. The others are
 * only counted. A deleted run shows its tokens as they were, a formula as a formula.
 *
 * <p>A table is not one run of tokens. It is compared by its rows and cells. See {@link #parts}.
 *
 * <p>The Myers diff in linear space of java-diff-utils aligns the tokens of two blocks.
 *
 * <ul>
 *   <li>Its middle snakes keep an inserted phrase whole. The greedy diff splits it at a word that
 *       it repeats. For example, "in ... and in combination".
 *   <li>Its whitespace merger joins changes that only a run of spaces separates. Thus a replaced
 *       phrase reads as one deletion and one insertion.
 *   <li>A change of spaces alone is not a change.
 * </ul>
 */
final class Marks {
  static final String INSERTED = "diff:ins";
  static final String DELETED = "diff:del";

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  private Marks() {}

  /**
   * Returns {@code to}, marked against {@code from}. The words of {@code to} that {@code from} does
   * not have are marked inserted. The words of {@code from} that {@code to} does not have are added
   * as deleted. Each deleted run goes before the token of {@code to} that it stood before.
   *
   * <p>A table is compared by its parts. See {@link #PARTS}.
   */
  static Marked between(Element from, Element to) {
    return from.name().equals(to.name()) && PARTS.contains(to.name())
        ? parts(from, to)
        : words(from, to);
  }

  /**
   * The elements that a comparison reads as a sequence of parts, not as one run of words. They are
   * a table and what is in it: its caption, its rows and groups of rows, its cells, and the lists,
   * units and notes in a cell. Thus the words deleted from a cell stay in that cell, and a row
   * inserted or deleted whole shows as a row.
   */
  private static final Set<String> PARTS =
      Set.of("table", "caption", "rowGroup", "tr", "td", "list", "unit", "notes", "note");

  /**
   * Returns {@code to}, marked against {@code from} part by part.
   *
   * <ul>
   *   <li>The parts are aligned by name and by what they say, with the Myers diff. Of the parts
   *       that differ between two that are the same, those alike pair. See {@link Likeness#pairs}.
   *   <li>The cells of two rows with as many cells are aligned by their column. Thus a changed cell
   *       is compared with the cell above it in the other text.
   *   <li>A part that is in both texts and differs is marked by {@link #between}.
   *   <li>A part that is only in {@code to} is marked inserted whole.
   *   <li>A part that is only in {@code from} is marked deleted whole, where it stood.
   * </ul>
   */
  private static Marked parts(Element from, Element to) {
    var before = from.children();
    var after = to.children();
    var replaced = new HashMap<Integer, Element>();
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var columns = to.name().equals("tr") && before.size() == after.size();
    var deltas =
        DiffUtils.diff(
                columns ? positions(before) : keys(before),
                columns ? positions(after) : keys(after))
            .getDeltas();
    for (var delta : deltas) {
      var source = delta.getSource();
      var target = delta.getTarget();
      var earlier = before.subList(source.getPosition(), source.getPosition() + source.size());
      var later = after.subList(target.getPosition(), target.getPosition() + target.size());
      var sourceOf =
          columns ? inOrder(earlier.size(), later.size()) : Likeness.pairs(earlier, later);
      for (var i = 0; i < target.size(); i++) {
        var part = later.get(i);
        var was = sourceOf[i] < 0 ? null : earlier.get(sourceOf[i]);
        if (was != null && Wording.key(was).equals(Wording.key(part))) {
          continue;
        }
        if (was != null && !was.name().equals(part.name())) {
          var gone = all(was, DELETED);
          deletedAt
              .computeIfAbsent(target.getPosition() + i, at -> new ArrayList<>())
              .add(gone.element());
          deleted += gone.deleted();
          was = null;
        }
        var marked = was == null ? all(part, INSERTED) : between(was, part);
        replaced.put(target.getPosition() + i, marked.element());
        inserted += marked.inserted();
        deleted += marked.deleted();
      }
      var used = new boolean[earlier.size()];
      for (var each : sourceOf) {
        if (each >= 0) {
          used[each] = true;
        }
      }
      for (var i = 0; i < earlier.size(); i++) {
        var part = earlier.get(i);
        if (used[i] || Wording.MARKS.contains(part.name())) {
          continue;
        }
        var gone = all(part, DELETED);
        deletedAt
            .computeIfAbsent(
                target.getPosition() + Likeness.before(sourceOf, i), at -> new ArrayList<>())
            .add(gone.element());
        deleted += gone.deleted();
      }
    }
    var children = new ArrayList<Element>();
    for (var i = 0; i < after.size(); i++) {
      children.addAll(deletedAt.getOrDefault(i, List.of()));
      children.add(replaced.getOrDefault(i, after.get(i)));
    }
    children.addAll(deletedAt.getOrDefault(after.size(), List.of()));
    return new Marked(copy(to, children), inserted, deleted);
  }

  /**
   * Returns {@code to} parts paired in order with {@code from} parts, as {@link Likeness#pairs}
   * returns them. The parts after the last of the shorter pair with none.
   */
  private static int[] inOrder(int from, int to) {
    var sourceOf = new int[to];
    for (var i = 0; i < to; i++) {
      sourceOf[i] = i < from ? i : -1;
    }
    return sourceOf;
  }

  /** Returns what each of {@code parts} is matched on: its name and its {@link Wording#key}. */
  private static List<String> keys(List<Element> parts) {
    return parts.stream().map(part -> part.name() + ":" + Wording.key(part)).toList();
  }

  /**
   * Returns the {@link #keys} of {@code parts}, each after its position. A part then only matches
   * the part in the same position, and two lists of as many parts align part by part.
   */
  private static List<String> positions(List<Element> parts) {
    var keys = keys(parts);
    var positioned = new ArrayList<String>();
    for (var i = 0; i < keys.size(); i++) {
      positioned.add(i + ":" + keys.get(i));
    }
    return positioned;
  }

  /** Returns {@code to}, marked against {@code from} word by word. */
  private static Marked words(Element from, Element to) {
    var before = new ArrayList<String>();
    var pieces = new ArrayList<Element>();
    walk(from, new Collect(before, pieces));
    var after = tokens(to);
    var beforeSaid = said(before);
    var afterSaid = said(after);
    var marked = new boolean[after.size()];
    var deletedAt = new HashMap<Integer, List<Element>>();
    var inserted = 0;
    var deleted = 0;
    var deltas =
        DiffRowGenerator.WHITESPACE_EQUALITIES_MERGER.apply(
            new InlineDeltaMergeInfo(
                DiffUtils.diff(
                        beforeSaid, afterSaid, new MyersDiffWithLinearSpace<String>(), null, false)
                    .getDeltas(),
                beforeSaid,
                afterSaid));
    for (var delta : deltas) {
      var source = delta.getSource();
      var target = delta.getTarget();
      if (spaces(source.getLines()) && spaces(target.getLines())) {
        continue;
      }
      for (var i = 0; i < target.size(); i++) {
        marked[target.getPosition() + i] = true;
      }
      inserted += words(target.getLines());
      if (source.size() > 0) {
        deletedAt
            .computeIfAbsent(target.getPosition(), at -> new ArrayList<>())
            .addAll(pieces.subList(source.getPosition(), source.getPosition() + source.size()));
        deleted += words(source.getLines());
      }
    }
    return new Marked(mark(to, INSERTED, marked, deletedAt), inserted, deleted);
  }

  /**
   * Returns {@code element} with every word in it marked {@code mark}, which is {@link #INSERTED}
   * or {@link #DELETED}.
   */
  static Marked all(Element element, String mark) {
    var tokens = tokens(element);
    var marked = new boolean[tokens.size()];
    Arrays.fill(marked, true);
    var words = words(said(tokens));
    var shown = mark(element, mark, marked, Map.of());
    return mark.equals(INSERTED) ? new Marked(shown, words, 0) : new Marked(shown, 0, words);
  }

  /**
   * An element as a comparison shows it, marked.
   *
   * @param element the element, its words marked
   * @param inserted how many words in it are inserted
   * @param deleted how many words in it are deleted
   */
  record Marked(Element element, int inserted, int deleted) {}

  /** Returns the tokens of {@code element}, in order. */
  static List<String> tokens(Element element) {
    var tokens = new ArrayList<String>();
    walk(element, new Collect(tokens, new ArrayList<>()));
    return tokens;
  }

  /**
   * Returns {@code element} with the tokens in {@code marked} wrapped in {@code mark}, and the
   * deleted words added.
   */
  private static Element mark(
      Element element, String mark, boolean[] marked, Map<Integer, List<Element>> deletedAt) {
    var marking = new Mark(mark, marked, deletedAt);
    var walked = walk(element, marking);
    // The element is the last in its place. A walk can put something before it, for example the
    // words deleted before the opening mark of a quote. Keep these in the element.
    var shown = walked.getLast();
    var children = new ArrayList<>(walked.subList(0, walked.size() - 1));
    children.addAll(shown.children());
    children.addAll(marking.deleted());
    return copy(shown, children);
  }

  /** Returns the tokens as they are compared. A run of spaces becomes one space. */
  private static List<String> said(List<String> tokens) {
    return tokens.stream().map(token -> SPACE.matcher(token).matches() ? " " : token).toList();
  }

  private static boolean spaces(List<String> tokens) {
    return tokens.stream().allMatch(String::isBlank);
  }

  /**
   * Returns how many of {@code tokens} are words. A word has a letter or a digit. Spaces and
   * punctuation alone are not words.
   */
  private static int words(List<String> tokens) {
    return (int) tokens.stream().filter(token -> WORD.matcher(token).find()).count();
  }

  /**
   * Returns {@code element} as {@code sink} rebuilds it. The walk visits its tokens in the order of
   * {@link Wording}. The result is what goes in the place of the element: one element, several
   * elements or none. For example, a {@code t} that is split at its marks gives several elements.
   */
  private static List<Element> walk(Element element, Sink sink) {
    var v = element.attributes().getOrDefault("v", "");
    switch (element.name()) {
      case "t" -> {
        return sink.words(element, Wording.words(v));
      }
      case "number" -> {
        return sink.atom(element, v, true);
      }
      case "eol" -> {
        return sink.atom(element, " ", false);
      }
      case "img" -> {
        return sink.atom(element, "[image " + element.attribute("src") + "]", true);
      }
      case "math" -> {
        // A formula is one token. A changed formula shows whole as deleted, then whole as
        // inserted: its MathML tokens alone do not read.
        return sink.atom(element, Wording.of(element), true);
      }
      default -> {
        if (Wording.MARKS.contains(element.name())) {
          return List.of(element);
        }
        if (!element.text().isEmpty()) {
          // The MathML tokens, for example mi and mo, hold text of their own. They are counted,
          // not marked.
          return sink.atom(element, element.text(), false);
        }
        var before = new ArrayList<Element>();
        var children = new ArrayList<Element>();
        if (element.name().equals("quote")) {
          before.addAll(sink.other(element.attributes().getOrDefault("startQuote", "")));
        }
        for (var child : element.children()) {
          children.addAll(walk(child, sink));
        }
        if (element.name().equals("quote")) {
          children.addAll(sink.other(element.attributes().getOrDefault("endQuote", "")));
        }
        before.add(copy(element, children));
        return before;
      }
    }
  }

  private static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }

  /** What a walk does at each token. */
  private interface Sink {
    /** Visits a {@code t} and its words. Returns what goes in its place. */
    List<Element> words(Element t, List<String> words);

    /** Visits an element that is one token. Returns what goes in its place. */
    List<Element> atom(Element element, String token, boolean markable);

    /**
     * Visits a token with no element of its own, for example the marks of a quote. Returns what
     * goes where it is.
     */
    List<Element> other(String token);
  }

  /**
   * A walk that only lists the tokens. It changes nothing.
   *
   * @param tokens the tokens, in order
   * @param pieces the element of each token, in order. It is what shows of the token if the token
   *     is deleted: a {@code t} with the word, or the element of the token, for example a formula.
   */
  private record Collect(List<String> tokens, List<Element> pieces) implements Sink {
    @Override
    public List<Element> words(Element t, List<String> words) {
      for (var word : words) {
        tokens.add(word);
        pieces.add(Mark.text(t, word));
      }
      return List.of(t);
    }

    @Override
    public List<Element> atom(Element element, String token, boolean markable) {
      tokens.add(token);
      pieces.add(element);
      return List.of(element);
    }

    @Override
    public List<Element> other(String token) {
      if (!token.isEmpty()) {
        tokens.add(token);
        pieces.add(Mark.text(null, token));
      }
      return List.of();
    }
  }

  /**
   * A walk that marks. It wraps the tokens in {@code marked} in {@code mark}. It puts the words
   * deleted before a token, from {@code deletedAt}, in {@code diff:del} where they stood.
   */
  private static final class Mark implements Sink {
    private final String mark;
    private final boolean[] marked;
    private final Map<Integer, List<Element>> deletedAt;
    private int next;

    Mark(String mark, boolean[] marked, Map<Integer, List<Element>> deletedAt) {
      this.mark = mark;
      this.marked = marked;
      this.deletedAt = deletedAt;
    }

    @Override
    public List<Element> words(Element t, List<String> words) {
      if (words.isEmpty()) {
        return List.of(t);
      }
      var out = new ArrayList<Element>();
      var run = new StringBuilder();
      var runMarked = false;
      for (var word : words) {
        var deleted = deletedAt.get(next);
        if (deleted != null || marked[next] != runMarked) {
          flush(t, run, runMarked, out);
          runMarked = marked[next];
        }
        if (deleted != null) {
          out.add(wrap(DELETED, deleted));
        }
        run.append(word);
        next++;
      }
      flush(t, run, runMarked, out);
      return out;
    }

    @Override
    public List<Element> atom(Element element, String token, boolean markable) {
      var out = deletedBefore();
      out.add(markable && marked[next] ? wrap(mark, element) : element);
      next++;
      return out;
    }

    @Override
    public List<Element> other(String token) {
      if (token.isEmpty()) {
        return List.of();
      }
      var out = deletedBefore();
      next++;
      return out;
    }

    /** Returns the words deleted after the last token. They go at the end of the marked element. */
    List<Element> deleted() {
      var deleted = deletedAt.get(next);
      return deleted == null ? List.of() : List.of(wrap(DELETED, deleted));
    }

    private List<Element> deletedBefore() {
      var out = new ArrayList<Element>();
      var deleted = deletedAt.get(next);
      if (deleted != null) {
        out.add(wrap(DELETED, deleted));
      }
      return out;
    }

    private void flush(Element t, StringBuilder run, boolean runMarked, List<Element> out) {
      if (run.isEmpty()) {
        return;
      }
      var piece = text(t, run.toString());
      out.add(runMarked ? wrap(mark, piece) : piece);
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

    private static Element wrap(String mark, Element element) {
      return new Element(mark, Map.of(), List.of(element), "");
    }

    /**
     * Returns {@code pieces} in {@code mark}. The words of a run of {@code t} with the same other
     * attributes join into one {@code t}.
     */
    private static Element wrap(String mark, List<Element> pieces) {
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
      return new Element(mark, Map.of(), joined, "");
    }

    /** Returns the attributes of {@code t} other than its words. */
    private static Map<String, String> others(Element t) {
      var others = new HashMap<>(t.attributes());
      others.remove("v");
      return others;
    }
  }
}
