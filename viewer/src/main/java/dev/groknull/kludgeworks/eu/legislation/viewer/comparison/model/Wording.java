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
 * <p>The wording leaves out what marks the text but does not say it. These marks can differ between
 * versions with the same wording:
 *
 * <ul>
 *   <li>the provenance of the amending acts
 *   <li>footnote references, which are renumbered when notes come and go
 *   <li>what a consolidation keeps of a deletion
 * </ul>
 *
 * <p>The EUR-Lex markers of a change, {@code changed}, keep their text.
 */
public final class Wording {
  /** The elements that say nothing, for a comparison. */
  static final Set<String> MARKS = Set.of("provenance", "noteRef", "deleted");

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

  private Wording() {}

  /** Returns what {@code block} says. For example, "The following means … shall be applied:". */
  public static String of(Element block) {
    var out = new StringBuilder();
    append(block, out);
    return SPACE.matcher(out).replaceAll(" ").strip();
  }

  /**
   * Returns what a comparison matches {@code block} on: its wording without spaces. A change of
   * spaces alone is not a change. For example, "DOB: 30 March 1977" and "DOB:30 March 1977" match.
   */
  public static String key(Element block) {
    return SPACE.matcher(of(block)).replaceAll("");
  }

  /**
   * Returns the words, spaces and punctuation of a wording, in order. Unicode segments them, with
   * the word {@link BreakIterator} of the JDK. A comparison of two wordings aligns these.
   */
  public static List<String> words(String wording) {
    var words = new ArrayList<String>();
    var breaks = BreakIterator.getWordInstance(Locale.ROOT);
    breaks.setText(wording);
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
        if (MARKS.contains(element.name())) {
          return;
        }
        // The MathML tokens, for example mi and mo, hold text of their own.
        out.append(element.text());
        element.children().forEach(child -> append(child, out));
      }
    }
  }
}
