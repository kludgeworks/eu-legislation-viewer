// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a block of a legal text says, as a comparison reads it.
 *
 * <p>The wording holds the text, numbers and quotation marks of the block. Line breaks become
 * spaces. Each run of spacing, a non-breaking space too, becomes a single space.
 *
 * <p>The wording leaves out the elements that say nothing. They can differ between versions with
 * the same wording:
 *
 * <ul>
 *   <li>the tags of the amending acts
 *   <li>footnote references, which are renumbered when notes come and go
 *   <li>what a consolidation keeps of a deletion
 * </ul>
 *
 * <p>The text of a change, {@code changed}, which a tag names, stays.
 */
public final class Wording {
  /** The elements that say nothing, for a comparison. */
  static final Set<String> SILENT = Set.of("tag", "noteRef", "deleted");

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

  private Wording() {}

  /** Returns what {@code block} says. For example, "The following means … shall be applied:". */
  public static String of(Element block) {
    var out = new StringBuilder();
    append(block, out);
    return SPACE.matcher(out).replaceAll(" ").strip();
  }

  /**
   * Returns what a comparison matches {@code block} on: its wording without spaces, without form
   * blanks ({@link #BLANK}), and with each apostrophe and quotation mark in its plain form ({@link
   * #plain}). A change of these alone is not a change. For example, "DOB: 30 March 1977" and
   * "DOB:30 March 1977" match.
   */
  public static String key(Element block) {
    return SPACE.matcher(BLANK.matcher(plain(of(block))).replaceAll("")).replaceAll("");
  }

  /**
   * A form blank: an ellipsis that stands alone as a word, where a form leaves room to fill in. For
   * example, "Make (trade name of manufacturer): …" and "Mass in running order: … kg". The
   * Publications Office chooses whether a consolidation prints it. An ellipsis joined to a word, as
   * in "Annex…", is not a blank.
   */
  private static final Pattern BLANK = Pattern.compile("(?<![\\p{L}\\p{N}])…(?![\\p{L}\\p{N}])");

  /**
   * Returns {@code text} with each apostrophe and single quotation mark as ', and each double
   * quotation mark as ". The Publications Office chooses these forms when it sets a text. No act
   * changes them, so a change of these forms alone is not a change. For example, "institution's"
   * and "institution’s" match, as do "“group”" and "\"group\"".
   */
  public static String plain(String text) {
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

  private static void append(Element element, StringBuilder out) {
    switch (element.name()) {
      case "t", "number" -> out.append(element.attributes().getOrDefault("v", ""));
      case "eol" -> out.append(' ');
      case "img" -> out.append(" [image ").append(element.attributes().get("src")).append("] ");
      case "quote" -> {
        out.append(element.attributes().getOrDefault("startQuote", ""));
        element.children().forEach(child -> append(child, out));
        out.append(element.attributes().getOrDefault("endQuote", ""));
      }
      default -> {
        if (SILENT.contains(element.name())) {
          return;
        }
        // The MathML tokens, for example mi and mo, hold text of their own.
        out.append(element.text());
        element.children().forEach(child -> append(child, out));
      }
    }
  }
}
