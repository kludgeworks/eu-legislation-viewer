// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.WhatCountsAsADifference.Read;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a block of a legal text says, as a comparison reads it.
 *
 * <p>The comparison reads a block as tokens, in this order:
 *
 * <ul>
 *   <li>the words, spaces and punctuation of a {@code t}, from {@link #words}
 *   <li>a whole {@code number}
 *   <li>an {@code eol}, which is a space
 *   <li>an image
 *   <li>the quotation marks of a quote
 *   <li>a whole formula
 * </ul>
 *
 * <p>The comparison leaves out a kept deletion: the words that an act deleted, which a
 * consolidation keeps marked as deleted. They are not words of the law. The tags of the amending
 * acts and the references to footnotes hold no words: their act and their marker are attributes.
 * The text of a change, {@code changed}, which a tag names, stays.
 *
 * <p>A walk visits the tokens of a block in this order, and a {@link Sink} rebuilds the block from
 * them. {@link #of} joins the tokens. {@link Edits} compares the tokens of two blocks. {@link Runs}
 * writes the edits of a block with a walk.
 */
public final class Wording {
  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

  private Wording() {}

  /**
   * Returns what {@code block} says: its tokens, joined. Each run of spacing, a non-breaking space
   * too, becomes one space. An image has a space before it and after it. For example, "The
   * following means … shall be applied:".
   */
  public static String of(Element block) {
    var out = new StringBuilder();
    walk(block, new Join(out));
    return SPACE.matcher(out).replaceAll(" ").strip();
  }

  /**
   * Returns what a comparison matches {@code block} on: its wording as the rules
   * form-of-apostrophes-and-quotes and spaces-are-no-change read a block. Two blocks with the same
   * key do not differ. For example, "DOB: 30 March 1977" and "DOB:30 March 1977" match.
   */
  public static String key(Element block) {
    var form = WhatCountsAsADifference.formOfApostrophesAndQuotes(new Read.Block(of(block)));
    return WhatCountsAsADifference.spacesAreNoChange(new Read.Block(form));
  }

  /**
   * Returns {@code text} with each apostrophe and single quotation mark as ', and each double
   * quotation mark as ". The Publications Office chooses these forms when it sets a text. No act
   * changes them, so a change of these forms alone is not a change. For example, "institution's"
   * and "institution’s" match, as do "“group”" and "\"group\"". The plain text has the length of
   * {@code text}.
   */
  static String plain(String text) {
    var out = new StringBuilder(text.length());
    for (var i = 0; i < text.length(); i++) {
      var c = text.charAt(i);
      out.append(
          switch (c) {
            case '‘', '’', '‚', '‛', 'ʼ', '‹', '›' -> '\'';
            case '“', '”', '„', '‟', '«', '»' -> '"';
            default -> c;
          });
    }
    return out.toString();
  }

  /**
   * Returns the words, spaces and punctuation of a wording, in order. Unicode segments them, with
   * the word {@link BreakIterator} of the JDK. A comparison of two wordings aligns these.
   *
   * <p>The breaks are those of the {@link #plain} wording. The JDK breaks "institution’s" at its
   * apostrophe, but not "institution's". Thus both forms give the same words.
   */
  public static List<String> words(String wording) {
    var words = new ArrayList<String>();
    var breaks = BreakIterator.getWordInstance(Locale.ROOT);
    // The plain wording has the same length, so its breaks are at the same places.
    breaks.setText(plain(wording));
    for (int start = breaks.first(), end = breaks.next();
        end != BreakIterator.DONE;
        start = end, end = breaks.next()) {
      words.add(wording.substring(start, end));
    }
    return words;
  }

  /** Returns the tokens of {@code element}, in order. */
  static List<String> tokens(Element element) {
    var tokens = new ArrayList<String>();
    collect(element, tokens, new ArrayList<>());
    return tokens;
  }

  /**
   * Lists the tokens of {@code element} in {@code tokens}, in order, and the element of each token
   * in {@code pieces}. The element of a token is what shows of it if it is deleted: a {@code t}
   * with the word, or the element of the token, for example a formula.
   */
  static void collect(Element element, List<String> tokens, List<Element> pieces) {
    walk(element, new Collect(tokens, pieces));
  }

  /**
   * Returns what {@code tokens} say, as the comparison compares them: each token as the rules
   * spaces-are-no-change and form-of-apostrophes-and-quotes read a token.
   */
  static List<String> said(List<String> tokens) {
    return tokens.stream()
        .map(token -> WhatCountsAsADifference.spacesAreNoChange(new Read.Token(token)))
        .map(token -> WhatCountsAsADifference.formOfApostrophesAndQuotes(new Read.Token(token)))
        .toList();
  }

  /**
   * Returns {@code element} as {@code sink} rebuilds it. The walk visits its tokens in order. The
   * result is what goes in the place of the element: one element, several elements or none. For
   * example, a {@code t} that is split at its edits gives several elements.
   */
  static List<Element> walk(Element element, Sink sink) {
    var v = element.attributes().getOrDefault("v", "");
    switch (element.name()) {
      case "t" -> {
        return sink.words(element, words(v));
      }
      case "number" -> {
        return sink.token(element, v, true);
      }
      case "eol" -> {
        return sink.token(element, " ", false);
      }
      case "img" -> {
        return sink.token(element, WhatCountsAsADifference.imageIsOnePiece(element), true);
      }
      case "figure" -> {
        return sink.figure(element);
      }
      case "math" -> {
        return sink.token(element, WhatCountsAsADifference.formulaIsOnePiece(element), true);
      }
      default -> {
        if (WhatCountsAsADifference.keptDeletionIsNotWords(element)) {
          return List.of(element);
        }
        if (!element.text().isEmpty()) {
          // The MathML elements, for example mi and mo, hold text of their own. They are counted,
          // without edits.
          return sink.token(element, element.text(), false);
        }
        if (element.name().equals("quote")) {
          return quote(element, sink);
        }
        var children = new ArrayList<Element>();
        for (var child : element.children()) {
          children.addAll(walk(child, sink));
        }
        return List.of(Differ.copy(element, children));
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
    var opening = sink.quotationMark(attributes.getOrDefault("startQuote", ""));
    var children = new ArrayList<Element>();
    if (opening.edited() != null) {
      children.add(opening.edited());
      attributes.put("startQuote", "");
    }
    for (var child : element.children()) {
      children.addAll(walk(child, sink));
    }
    var closing = sink.quotationMark(attributes.getOrDefault("endQuote", ""));
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

  /** What a walk does at each token. */
  interface Sink {
    /** Visits a {@code t} and its words. Returns what goes in its place. */
    List<Element> words(Element t, List<String> words);

    /**
     * Visits an element that is one token, which says {@code said}. Returns what goes in its place.
     */
    List<Element> token(Element element, String said, boolean editable);

    /** Visits a figure with its optional image file reference and child caption or description. */
    List<Element> figure(Element element);

    /** Visits a quotation mark of a quote, which has no element of its own. */
    QuotationMark quotationMark(String mark);
  }

  /**
   * What a walk makes of a quotation mark of a quote.
   *
   * @param before what goes before the quotation mark, for example the words deleted there
   * @param edited the quotation mark as edited text. Null if it is without edits: it then stays as
   *     it is.
   */
  record QuotationMark(List<Element> before, @Nullable Element edited) {
    static final QuotationMark NONE = new QuotationMark(List.of(), null);
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
        pieces.add(Runs.text(t, word));
      }
      return List.of(t);
    }

    @Override
    public List<Element> token(Element element, String said, boolean editable) {
      tokens.add(said);
      pieces.add(element);
      return List.of(element);
    }

    @Override
    public List<Element> figure(Element element) {
      tokens.add(of(element));
      pieces.add(element);
      return List.of(element);
    }

    @Override
    public QuotationMark quotationMark(String mark) {
      if (!mark.isEmpty()) {
        tokens.add(mark);
        pieces.add(Runs.text(null, mark));
      }
      return QuotationMark.NONE;
    }
  }

  /** A walk that joins the tokens into {@code out}. It changes nothing. */
  private record Join(StringBuilder out) implements Sink {
    @Override
    public List<Element> words(Element t, List<String> words) {
      words.forEach(out::append);
      return List.of(t);
    }

    @Override
    public List<Element> token(Element element, String said, boolean editable) {
      if (element.name().equals("img")) {
        out.append(' ').append(said).append(' ');
      } else {
        out.append(said);
      }
      return List.of(element);
    }

    @Override
    public List<Element> figure(Element element) {
      if (WhatCountsAsADifference.figureHasFile(element)) {
        out.append(' ').append(WhatCountsAsADifference.imageIsOnePiece(element)).append(' ');
      }
      for (var child : element.children()) {
        if (!out.isEmpty()) {
          out.append(' ');
        }
        walk(child, this);
      }
      return List.of(element);
    }

    @Override
    public QuotationMark quotationMark(String mark) {
      out.append(mark);
      return QuotationMark.NONE;
    }
  }
}
