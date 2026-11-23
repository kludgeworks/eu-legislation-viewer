// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.myers.MyersDiffWithLinearSpace;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.deltamerge.InlineDeltaMergeInfo;
import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Shows the edits of a comparison in legal-text: its insertions and deletions. Inserted words go in
 * {@code diff:ins}, and deleted words go in {@code diff:del}. The edits are only around text, a
 * formula or an image, that is the words of a {@code t}, a {@code number}, a {@code math} or an
 * {@code img}. A quotation mark of a {@code quote} that is edited becomes such text. Thus an
 * element keeps the parent that it has in the text.
 *
 * <p>A block is read as tokens, in the order in which {@link Wording} reads it:
 *
 * <ul>
 *   <li>the words, spaces and punctuation of a {@code t}, from {@link Wording#words}
 *   <li>a whole {@code number}
 *   <li>an {@code eol}
 *   <li>an image
 *   <li>the quotation marks of a quote
 *   <li>a whole formula
 * </ul>
 *
 * <p>The tokens of a {@code t}, a {@code number}, a formula and an image are edited, and so are the
 * quotation marks of a quote. The others are only counted. A deleted run shows its tokens as they
 * were, a formula as a formula.
 *
 * <p>A table is not one run of tokens. It is compared by its rows and cells. See {@link #pieces}.
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
final class Edits {
  static final String INSERTED = "diff:ins";
  static final String DELETED = "diff:del";

  /**
   * The attribute of a footnote, or of a reference to a footnote, that only one of the two texts
   * has. Its value is "ins" or "del". The marker of a footnote and a reference to a footnote are
   * not words, so no edit is around them. With the attribute, a page can show them only with the
   * text that has them.
   */
  static final String ONLY = "diff";

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  private Edits() {}

  /**
   * Returns {@code to}, edited against {@code from}. The words of {@code to} that {@code from} does
   * not have are shown inserted. The words of {@code from} that {@code to} does not have are added
   * as deleted. Each deleted run goes before the token of {@code to} that it stood before.
   *
   * <p>A table is compared by its pieces. See {@link #PIECES}.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-the-edits-show")
  static Edited between(Element from, Element to) {
    return from.name().equals(to.name()) && PIECES.contains(to.name())
        ? pieces(from, to)
        : words(from, to);
  }

  /**
   * The elements that a comparison reads as a sequence of pieces, not as one run of words. They are
   * a table and what is in it: its caption, its rows and groups of rows, its cells, and the lists,
   * occurrences and footnotes in a cell. Thus the words deleted from a cell stay in that cell, and
   * a row inserted or deleted whole shows as a row.
   */
  private static final Set<String> PIECES =
      Set.of(
          "table",
          "caption",
          "rowGroup",
          "tr",
          "td",
          "list",
          "occurrence",
          "footnotes",
          "footnote");

  /**
   * Returns {@code to}, edited against {@code from} piece by piece.
   *
   * <ul>
   *   <li>The pieces match by name and by what they say, and those that differ pair if they are
   *       alike. See {@link Pieces}.
   *   <li>A piece that is in both texts and differs gets its edits from {@link #between}.
   *   <li>A piece that is only in {@code to} is shown inserted whole.
   *   <li>A piece that is only in {@code from} is shown deleted whole, where it stood.
   * </ul>
   */
  @BusinessRule(group = RuleGroup.HOW_TWO_TEXTS_PAIR, name = "table-row-by-row")
  private static Edited pieces(Element from, Element to) {
    var before = from.children();
    var after = to.children();
    var differing = Pieces.differing(before, after, keys(before), keys(after));
    var shown = new Pieces.Shown();
    for (var each : differing) {
      switch (each) {
        case Pieces.Changed(int at, Element was, Element is) -> {
          if (was.name().equals(is.name())) {
            shown.replace(at, between(was, is));
          } else {
            shown.delete(at, all(was, DELETED));
            shown.replace(at, all(is, INSERTED));
          }
        }
        case Pieces.Inserted(int at, Element is) -> shown.replace(at, all(is, INSERTED));
        case Pieces.Deleted(int at, Element was) -> {
          if (!Wording.SILENT.contains(was.name())) {
            shown.delete(at, all(was, DELETED));
          }
        }
      }
    }
    return new Edited(copy(to, shown.of(after)), shown.inserted(), shown.deleted());
  }

  /** Returns what each of {@code pieces} is matched on: its name and its {@link Wording#key}. */
  private static List<String> keys(List<Element> pieces) {
    return pieces.stream().map(piece -> piece.name() + ":" + Wording.key(piece)).toList();
  }

  /** Returns {@code to}, edited against {@code from} word by word. */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "replaced-phrase")
  private static Edited words(Element from, Element to) {
    var before = new ArrayList<String>();
    var pieces = new ArrayList<Element>();
    walk(from, new Collect(before, pieces));
    var after = tokens(to);
    var beforeSaid = said(before);
    var afterSaid = said(after);
    var edited = new boolean[after.size()];
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
        edited[target.getPosition() + i] = true;
      }
      inserted += words(target.getLines());
      if (source.size() > 0) {
        deletedAt
            .computeIfAbsent(target.getPosition(), at -> new ArrayList<>())
            .addAll(pieces.subList(source.getPosition(), source.getPosition() + source.size()));
        deleted += words(source.getLines());
      }
    }
    return new Edited(edit(to, INSERTED, edited, deletedAt), inserted, deleted);
  }

  /**
   * Returns {@code element} with every word in it edited {@code edit}, which is {@link #INSERTED}
   * or {@link #DELETED}.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-the-edits-show")
  static Edited all(Element element, String edit) {
    var tokens = tokens(element);
    var edited = new boolean[tokens.size()];
    Arrays.fill(edited, true);
    var words = words(said(tokens));
    var shown = only(edit(element, edit, edited, Map.of()), edit.equals(INSERTED) ? "ins" : "del");
    return edit.equals(INSERTED) ? new Edited(shown, words, 0) : new Edited(shown, 0, words);
  }

  /**
   * Returns {@code element} with each footnote and each reference to a footnote in it set {@link
   * #ONLY}.
   */
  private static Element only(Element element, String value) {
    if (element.name().equals("footnote") || element.name().equals("footnoteRef")) {
      var attributes = new HashMap<>(element.attributes());
      attributes.put(ONLY, value);
      return new Element(element.name(), attributes, element.children(), element.text());
    }
    return copy(element, element.children().stream().map(child -> only(child, value)).toList());
  }

  /**
   * Returns {@code element} with each reference to an inserted footnote of it set {@link #ONLY}. A
   * block that changed can refer to a footnote that only the later text has. The reference is then
   * only in the later text also.
   */
  static Element referring(Element element) {
    var inserted = new HashSet<String>();
    insertedFootnotes(element, inserted);
    return inserted.isEmpty() ? element : referring(element, inserted);
  }

  private static void insertedFootnotes(Element element, Set<String> found) {
    if (element.name().equals("footnote")) {
      var eId = element.attribute("eId");
      if (eId != null && "ins".equals(element.attribute(ONLY))) {
        found.add("#" + eId);
      }
      return;
    }
    element.children().forEach(child -> insertedFootnotes(child, found));
  }

  private static Element referring(Element element, Set<String> inserted) {
    if (element.name().equals("footnoteRef")) {
      return element.attribute(ONLY) == null && inserted.contains(element.attribute("href"))
          ? only(element, "ins")
          : element;
    }
    return copy(
        element, element.children().stream().map(child -> referring(child, inserted)).toList());
  }

  /**
   * An element as a comparison shows it, edited.
   *
   * @param element the element, its words edited
   * @param inserted how many words in it are inserted
   * @param deleted how many words in it are deleted
   */
  record Edited(Element element, int inserted, int deleted) {}

  /** Returns the tokens of {@code element}, in order. */
  static List<String> tokens(Element element) {
    var tokens = new ArrayList<String>();
    walk(element, new Collect(tokens, new ArrayList<>()));
    return tokens;
  }

  /**
   * Returns {@code element} with the tokens in {@code edited} wrapped in {@code edit}, and the
   * deleted words added.
   */
  private static Element edit(
      Element element, String edit, boolean[] edited, Map<Integer, List<Element>> deletedAt) {
    var editing = new Editing(edit, edited, deletedAt);
    var walked = walk(element, editing);
    // The element is the last in its place. A walk can put something before it, for example the
    // words deleted before the opening quotation mark of a quote. Keep these in the element.
    var shown = walked.getLast();
    var children = new ArrayList<>(walked.subList(0, walked.size() - 1));
    children.addAll(shown.children());
    children.addAll(editing.deleted());
    return copy(shown, children);
  }

  /** Returns the tokens as they are compared. A run of spaces becomes one space. */
  /**
   * Returns what {@code tokens} say, as the comparison compares them: a run of spaces as one space,
   * and a word with each apostrophe in its plain form ({@link Wording#plain}). Thus "institution's"
   * and "institution’s" are the same word. A quotation mark alone keeps its form: the plain form of
   * an opening quotation mark and of a closing one is the same, and they would then match one with
   * the other.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "spaces-are-no-change")
  @BusinessRule(
      group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE,
      name = "form-of-apostrophes-and-quotes")
  private static List<String> said(List<String> tokens) {
    return tokens.stream()
        .map(
            token ->
                SPACE.matcher(token).matches()
                    ? " "
                    : token.codePoints().anyMatch(Character::isLetterOrDigit)
                        ? Wording.plain(token)
                        : token)
        .toList();
  }

  private static boolean spaces(List<String> tokens) {
    return tokens.stream().allMatch(String::isBlank);
  }

  /**
   * Returns how many of {@code tokens} are words. A word has a letter or a digit. Spaces and
   * punctuation alone are not words.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "what-a-word-is")
  private static int words(List<String> tokens) {
    return (int) tokens.stream().filter(token -> WORD.matcher(token).find()).count();
  }

  /**
   * Returns {@code element} as {@code sink} rebuilds it. The walk visits its tokens in the order of
   * {@link Wording}. The result is what goes in the place of the element: one element, several
   * elements or none. For example, a {@code t} that is split at its edits gives several elements.
   */
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "formula-is-one-piece")
  @BusinessRule(group = RuleGroup.WHAT_COUNTS_AS_A_DIFFERENCE, name = "kept-deletion-is-not-words")
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
        if (Wording.SILENT.contains(element.name())) {
          return List.of(element);
        }
        if (!element.text().isEmpty()) {
          // The MathML tokens, for example mi and mo, hold text of their own. They are counted,
          // without edits.
          return sink.atom(element, element.text(), false);
        }
        if (element.name().equals("quote")) {
          return quote(element, sink);
        }
        var children = new ArrayList<Element>();
        for (var child : element.children()) {
          children.addAll(walk(child, sink));
        }
        return List.of(copy(element, children));
      }
    }
  }

  /**
   * Returns the quote {@code element} as {@code sink} rebuilds it. Its quotation marks are tokens,
   * and legal-text has them as attributes. A quotation mark that is edited becomes text in the
   * quote, in its edit element, and the attribute is emptied. Thus it shows where it stands, edited
   * as any other text is.
   */
  private static List<Element> quote(Element element, Sink sink) {
    var attributes = new HashMap<>(element.attributes());
    var opening = sink.other(attributes.getOrDefault("startQuote", ""));
    var children = new ArrayList<Element>();
    if (opening.edited() != null) {
      children.add(opening.edited());
      attributes.put("startQuote", "");
    }
    for (var child : element.children()) {
      children.addAll(walk(child, sink));
    }
    var closing = sink.other(attributes.getOrDefault("endQuote", ""));
    children.addAll(closing.before());
    if (closing.edited() != null) {
      children.add(closing.edited());
      attributes.put("endQuote", "");
    }
    // What was deleted before the opening quotation mark goes before the quote.
    var out = new ArrayList<>(opening.before());
    out.add(new Element(element.name(), attributes, children, element.text()));
    return out;
  }

  private static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }

  /** What a walk does at each token. */
  private interface Sink {
    /** Visits a {@code t} and its words. Returns what goes in its place. */
    List<Element> words(Element t, List<String> words);

    /** Visits an element that is one token. Returns what goes in its place. */
    List<Element> atom(Element element, String token, boolean editable);

    /** Visits a token with no element of its own: a quotation mark of a quote. */
    Other other(String token);
  }

  /**
   * What a walk makes of a token with no element of its own.
   *
   * @param before what goes before the token, for example the words deleted there
   * @param edited the token as edited text. Null if the token is without edits: it then stays as it
   *     is.
   */
  private record Other(List<Element> before, @Nullable Element edited) {
    static final Other NONE = new Other(List.of(), null);
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
        pieces.add(Editing.text(t, word));
      }
      return List.of(t);
    }

    @Override
    public List<Element> atom(Element element, String token, boolean editable) {
      tokens.add(token);
      pieces.add(element);
      return List.of(element);
    }

    @Override
    public Other other(String token) {
      if (!token.isEmpty()) {
        tokens.add(token);
        pieces.add(Editing.text(null, token));
      }
      return Other.NONE;
    }
  }

  /**
   * A walk that edits. It wraps the tokens in {@code edited} in {@code edit}. It puts the words
   * deleted before a token, from {@code deletedAt}, in {@code diff:del} where they stood.
   */
  private static final class Editing implements Sink {
    private final String edit;
    private final boolean[] edited;
    private final Map<Integer, List<Element>> deletedAt;
    private int next;

    Editing(String edit, boolean[] edited, Map<Integer, List<Element>> deletedAt) {
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
          out.add(wrap(DELETED, deleted));
        }
        run.append(word);
        next++;
      }
      flush(t, run, runEdited, out);
      return out;
    }

    @Override
    public List<Element> atom(Element element, String token, boolean editable) {
      var out = deletedBefore();
      out.add(editable && edited[next] ? wrap(edit, element) : element);
      next++;
      return out;
    }

    @Override
    public Other other(String token) {
      if (token.isEmpty()) {
        return Other.NONE;
      }
      var before = deletedBefore();
      var shown = edited[next] ? wrap(edit, text(null, token)) : null;
      next++;
      return new Other(before, shown);
    }

    /** Returns the words deleted after the last token. They go at the end of the edited element. */
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
  }
}
