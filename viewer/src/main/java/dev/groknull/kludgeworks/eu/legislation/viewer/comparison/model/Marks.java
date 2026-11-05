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
 * Words marked in legal-text, as a comparison shows them: inserted in diff:ins, deleted in
 * diff:del, only ever around text (a t's words, a number), so an element keeps the parent it has in
 * the text. A block is read as tokens, in the order Wording reads it: a t's words, spaces and
 * punctuation (Wording.words), a number whole, an eol, an image, a quote's marks and MathML's
 * tokens; only a t's and a number's are marked, the others counted. Two blocks' tokens are aligned
 * by Myers's diff in linear space (java-diff-utils): its middle snakes keep a phrase inserted
 * whole, where the greedy one splits it at a word it repeats (in ... and in combination); a run of
 * spaces between changes joins them (its whitespace merger), so a phrase replaced reads as one
 * deletion and one insertion; a change of spaces alone is none.
 */
final class Marks {
  static final String INSERTED = "diff:ins";
  static final String DELETED = "diff:del";

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  private Marks() {}

  /**
   * {@code to}, its words that {@code from} hasn't inserted and {@code from}'s that it hasn't
   * deleted, before the token they stood before in {@code to}.
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

  /** {@code element}, every word in it marked {@code mark} (INSERTED or DELETED). */
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

  /** The tokens of {@code element}, in order. */
  static List<String> tokens(Element element) {
    var tokens = new ArrayList<String>();
    walk(element, new Collect(tokens));
    return tokens;
  }

  /** {@code element}, the tokens {@code marked} wrapped in {@code mark}, and deleted ones added. */
  private static Element mark(
      Element element, String mark, boolean[] marked, Map<Integer, String> deletedAt) {
    var marking = new Mark(mark, marked, deletedAt);
    var walked = walk(element, marking);
    // the element is the last in its place; what a walk puts before it (deleted before a quote's
    // opening mark) is kept in it
    var shown = walked.getLast();
    var children = new ArrayList<>(walked.subList(0, walked.size() - 1));
    children.addAll(shown.children());
    children.addAll(marking.deleted());
    return copy(shown, children);
  }

  /** The tokens as compared: a run of spaces as one space. */
  private static List<String> said(List<String> tokens) {
    return tokens.stream().map(token -> SPACE.matcher(token).matches() ? " " : token).toList();
  }

  private static boolean spaces(List<String> tokens) {
    return tokens.stream().allMatch(String::isBlank);
  }

  /**
   * How many of {@code tokens} are words: with a letter or digit, not only spaces or punctuation.
   */
  private static int words(List<String> tokens) {
    return (int) tokens.stream().filter(token -> WORD.matcher(token).find()).count();
  }

  /**
   * {@code element} as {@code sink} rebuilds it, its tokens visited in Wording's order: what is in
   * its place, one element or several (a t split at its marks), or none.
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
          // MathML's tokens hold text of their own (mi, mo): counted, not marked
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
    /** A t, its words: what is in its place. */
    List<Element> words(Element t, List<String> words);

    /** An element that is one token: what is in its place. */
    List<Element> atom(Element element, String token, boolean markable);

    /** A token with no element of its own (a quote's marks): what goes where it is. */
    List<Element> other(String token);
  }

  /** A walk that only lists the tokens, changing nothing. */
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
   * A walk that marks: the tokens {@code marked} wrapped in {@code mark}, and the words deleted
   * before a token ({@code deletedAt}) in diff:del where they stood.
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

    /** The words deleted after the last token, which go at the end of the element marked. */
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

    /** A t saying {@code v}, with {@code like}'s other attributes (none if it has none). */
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
