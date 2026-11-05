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
 * What a block of a legal text says, as a comparison reads it: its text, numbers and quotation
 * marks, its line breaks as spaces, its spacing as single spaces (a non-breaking one too). Left out
 * is what marks the text rather than says it, and differs between versions whose wording is the
 * same: the amending acts' provenance, footnote references (renumbered as notes come and go), and
 * what a consolidation keeps of a deletion. EUR-Lex's markers of a change (changed) keep their
 * text.
 */
public final class Wording {
  /** What says nothing, for a comparison. */
  private static final Set<String> MARKS = Set.of("provenance", "noteRef", "deleted");

  private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0]+");

  private Wording() {}

  /** What {@code block} says: The following means … shall be applied:. */
  public static String of(Element block) {
    var out = new StringBuilder();
    append(block, out);
    return SPACE.matcher(out).replaceAll(" ").strip();
  }

  /**
   * Its words, spaces and punctuation, in order, as Unicode segments them (the JDK's word
   * BreakIterator): what a comparison of two wordings aligns.
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
        // MathML's tokens hold text of their own (mi, mo)
        out.append(element.text());
        element.children().forEach(child -> append(child, out));
      }
    }
  }
}
