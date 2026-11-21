// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Names occurrences without a number by their position, as the Joint Practical Guide does (15.4):
 * "the first subparagraph", "the second paragraph". Folds spell the position out ("First and second
 * subparagraphs"); outline rows are short ("Art. 9, 2nd subpara.").
 *
 * <p>The converter gives an occurrence without a number an eId that ends with its position among
 * the occurrences of its kind in its parent, from 1. For example, {@code art_3/par_1/sub_2} is the
 * second subparagraph of Article 3(1), and {@code art_2/unp_1} the first paragraph of Article 2,
 * which has no number.
 */
final class Positions {
  private Positions() {}

  /** The last segment of an eId with a position. For example, {@code sub_2}. */
  private static final Pattern POSITION = Pattern.compile("(?:^|/)[a-z]+_(\\d+)$");

  /** An article in an eId. For example, {@code art_9}. */
  private static final Pattern ARTICLE = Pattern.compile("(?:^|/)art_([^/]+)");

  /** A numbered paragraph in an eId. For example, {@code par_2}. */
  private static final Pattern PARAGRAPH = Pattern.compile("(?:^|/)par_([^/]+)");

  private static final List<String> ORDINALS =
      List.of(
          "first",
          "second",
          "third",
          "fourth",
          "fifth",
          "sixth",
          "seventh",
          "eighth",
          "ninth",
          "tenth",
          "eleventh",
          "twelfth",
          "thirteenth",
          "fourteenth",
          "fifteenth",
          "sixteenth",
          "seventeenth",
          "eighteenth",
          "nineteenth",
          "twentieth");

  /** Returns the position of the occurrence {@code eId}, which has no number, from 1. */
  static OptionalInt of(String eId) {
    var position = POSITION.matcher(eId);
    return position.find()
        ? OptionalInt.of(Integer.parseInt(position.group(1)))
        : OptionalInt.empty();
  }

  /** Returns {@code n} as an ordinal in words. For example, "second", or "21st" past twentieth. */
  static String ordinal(int n) {
    return n >= 1 && n <= ORDINALS.size() ? ORDINALS.get(n - 1) : shortOrdinal(n);
  }

  /** Returns {@code n} as a short ordinal. For example, "2nd", "11th" or "23rd". */
  static String shortOrdinal(int n) {
    var teen = n % 100 >= 11 && n % 100 <= 13;
    var suffix =
        teen
            ? "th"
            : switch (n % 10) {
              case 1 -> "st";
              case 2 -> "nd";
              case 3 -> "rd";
              default -> "th";
            };
    return n + suffix;
  }

  /**
   * Returns the label of a fold of {@code occurrences} that have no number, by their positions. For
   * example, "First subparagraph", "First and second subparagraphs" or "Second to fourth indents".
   * Returns empty if they are not all of one kind, or if any has no position.
   */
  static Optional<String> spelled(List<Sibling> occurrences, String noun) {
    var kind = occurrences.getFirst().kind();
    if (occurrences.stream().anyMatch(occurrence -> !occurrence.kind().equals(kind))) {
      return Optional.empty();
    }
    var first = of(occurrences.getFirst().eId());
    var last = of(occurrences.getLast().eId());
    if (first.isEmpty()
        || last.isEmpty()
        || occurrences.stream().anyMatch(occurrence -> of(occurrence.eId()).isEmpty())) {
      return Optional.empty();
    }
    var start = capital(ordinal(first.getAsInt()));
    if (occurrences.size() == 1) {
      return Optional.of(start + " " + noun);
    }
    return Optional.of(
        start
            + (occurrences.size() == 2 ? " and " : " to ")
            + ordinal(last.getAsInt())
            + " "
            + noun
            + "s");
  }

  /**
   * Returns the short label of the outline row of the occurrence {@code eId} of {@code kind}, which
   * has no number. For example, "Art. 9, 2nd subpara." or "Art. 3(1), 2nd subpara.". Returns empty
   * if it has no position.
   */
  static Optional<String> row(String eId, String kind) {
    var position = of(eId);
    if (position.isEmpty()) {
      return Optional.empty();
    }
    var where = new StringBuilder();
    var article = ARTICLE.matcher(eId);
    if (article.find()) {
      where.append("Art. ").append(article.group(1));
      var paragraph = PARAGRAPH.matcher(eId);
      if (paragraph.find()) {
        where.append('(').append(paragraph.group(1)).append(')');
      }
      where.append(", ");
    }
    return Optional.of(where + shortOrdinal(position.getAsInt()) + " " + abbreviated(kind));
  }

  private static String abbreviated(String kind) {
    return switch (kind) {
      case "subparagraph" -> "subpara.";
      case "paragraph" -> "para.";
      default -> kind;
    };
  }

  private static String capital(String word) {
    return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
  }
}
