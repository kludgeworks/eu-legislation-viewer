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
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Marks words in legal-text, as a comparison shows them. Inserted words go in {@code diff:ins}, and
 * deleted words go in {@code diff:del}. The marks are only around text, that is the words of a
 * {@code t} or a {@code number}. Thus an element keeps the parent that it has in the text.
 *
 * <p>A block is read as tokens, in the order in which {@link Wording} reads it:
 *
 * <ul>
 *   <li>the words, spaces and punctuation of a {@code t}, from {@link Wording#words}
 *   <li>a whole {@code number}
 *   <li>an {@code eol}
 *   <li>an image
 *   <li>the marks of a quote
 *   <li>the MathML tokens
 * </ul>
 *
 * <p>Only the tokens of a {@code t} and of a {@code number} are marked. The others are only
 * counted.
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
   */
  static Marked between(Element from, Element to) {
    var before = tokens(from);
    var after = tokens(to);
    var beforeSaid = said(before);
    var afterSaid = said(after);
    var marked = new boolean[after.size()];
    var deletedAt = new HashMap<Integer, String>();
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
        var text =
            String.join(
                "", before.subList(source.getPosition(), source.getPosition() + source.size()));
        deletedAt.merge(target.getPosition(), text, String::concat);
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
    walk(element, new Collect(tokens));
    return tokens;
  }

  /**
   * Returns {@code element} with the tokens in {@code marked} wrapped in {@code mark}, and the
   * deleted words added.
   */
  private static Element mark(
      Element element, String mark, boolean[] marked, Map<Integer, String> deletedAt) {
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
        return sink.atom(element, "[image " + element.attribute("src") + "]", false);
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

  /** A walk that only lists the tokens. It changes nothing. */
  private record Collect(List<String> tokens) implements Sink {
    @Override
    public List<Element> words(Element t, List<String> words) {
      tokens.addAll(words);
      return List.of(t);
    }

    @Override
    public List<Element> atom(Element element, String token, boolean markable) {
      tokens.add(token);
      return List.of(element);
    }

    @Override
    public List<Element> other(String token) {
      if (!token.isEmpty()) {
        tokens.add(token);
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
    private final Map<Integer, String> deletedAt;
    private int next;

    Mark(String mark, boolean[] marked, Map<Integer, String> deletedAt) {
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
          out.add(wrap(DELETED, text(t, deleted)));
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
      return deleted == null ? List.of() : List.of(wrap(DELETED, text(null, deleted)));
    }

    private List<Element> deletedBefore() {
      var out = new ArrayList<Element>();
      var deleted = deletedAt.get(next);
      if (deleted != null) {
        out.add(wrap(DELETED, text(null, deleted)));
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
    private static Element text(@Nullable Element like, String v) {
      var attributes = new HashMap<String, String>(like == null ? Map.of() : like.attributes());
      attributes.put("v", v);
      return new Element("t", attributes, List.of(), "");
    }

    private static Element wrap(String mark, Element element) {
      return new Element(mark, Map.of(), List.of(element), "");
    }
  }
}
