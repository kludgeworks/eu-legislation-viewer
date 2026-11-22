// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A subdivision of an act, named in words as the pages name it. The names follow the method of
 * reference of the Joint Practical Guide (15.4). For example:
 *
 * <ul>
 *   <li>Article 89(1)
 *   <li>point 8.1.2.3(c), where a lettered point follows the point that it is in
 *   <li>Annex II, Part VI, Section 3.3
 *   <li>Appendix 4-C, point (c), indent 6
 *   <li>point 1.2.6.2, paragraph 2
 *   <li>the annex, for the one annex of an act
 * </ul>
 *
 * <p>A place is named from its levels, from the outermost in ({@link #of(List)}):
 *
 * <ul>
 *   <li>A paragraph of an article, and a point with a number in brackets of an article or a point,
 *       is cited after it: Article 1(4)(c)(v), point 8.1.2.3(c).
 *   <li>An article is numbered across the act, so what holds it is left out.
 *   <li>A level whose number starts the number of the next level is left out: point 6.8.1.6 is in
 *       chapter 6, and Appendix 5-A is in chapter 5. The one annex of an act is left out too.
 *   <li>A subparagraph is not cited, and a level without a number is passed.
 * </ul>
 *
 * @param words the name of the place. For example, "Article 89(1)".
 */
@ValueObject
public record Place(String words) {

  /**
   * One level of a place.
   *
   * @param noun what the level is, as a page names it. For example, "Article", "point" or "Annex".
   * @param number its number as the act writes it, with brackets where it has them. For example,
   *     "89", "8.1.2.3" or "(c)". Empty if it has no number.
   */
  @ValueObject
  public record Level(String noun, String number) {}

  /** The noun of a part of a name. For example, "Article" or "point". */
  private static final Pattern NOUN = Pattern.compile("[A-Za-z]+");

  /**
   * Returns the place that {@code levels} name, from the outermost in. Returns empty if no level
   * names an occurrence.
   */
  public static Optional<Place> of(List<Level> levels) {
    var named = new ArrayList<String>();
    String noun = null;
    var cited = "";
    for (var level : levels) {
      if (level.number().isEmpty()) {
        if (level.noun().equals("Annex")) {
          noun = "the annex";
          cited = "";
        }
        continue;
      }
      var number = level.number();
      if (level.noun().equals("subparagraph") && noun != null) {
        continue;
      } else if (number.startsWith("(")
          && ("Article".equals(noun) || "point".equals(noun))
          && !cited.isEmpty()) {
        cited += number;
      } else if (level.noun().equals("paragraph") && "Article".equals(noun)) {
        cited += "(" + number + ")";
      } else {
        if (level.noun().equals("Article")) {
          named.clear();
        } else if (noun != null && holds(noun, cited, number)) {
          named.add(cited.isEmpty() ? noun : noun + " " + cited);
        }
        noun = level.noun();
        cited = number;
      }
    }
    if (noun == null) {
      return Optional.empty();
    }
    named.add(cited.isEmpty() ? noun : noun + " " + cited);
    return Optional.of(new Place(String.join(", ", named)));
  }

  /**
   * Returns the levels that the name says, from the outermost in. A number in brackets right after
   * an article's number is its paragraph if it starts with a digit, and a point if not. Any other
   * number in brackets is a point. For example, "Article 1(4)(c)" is Article 1, paragraph 4 and
   * point (c).
   *
   * @throws IllegalArgumentException if a part of the name is not a noun and a number
   */
  public List<Level> levels() {
    var levels = new ArrayList<Level>();
    for (var part : words.split(", ")) {
      if (part.equals("the annex")) {
        levels.add(new Level("Annex", ""));
        continue;
      }
      var space = part.indexOf(' ');
      var noun = space < 0 ? "" : part.substring(0, space);
      if (!NOUN.matcher(noun).matches()) {
        throw new IllegalArgumentException("Not a place: " + words);
      }
      var rest = part.substring(space + 1);
      var open = rest.indexOf('(');
      var number = open < 0 ? rest : rest.substring(0, open);
      var brackets = open < 0 ? List.<String>of() : bracketed(rest.substring(open), words);
      var taken = 0;
      if (number.isEmpty()) {
        if (brackets.isEmpty()) {
          throw new IllegalArgumentException("Not a place: " + words);
        }
        levels.add(new Level(noun, brackets.getFirst()));
        taken = 1;
      } else {
        levels.add(new Level(noun, number));
        if (noun.equals("Article") && !brackets.isEmpty()) {
          levels.add(paragraphOrPoint(brackets.getFirst()));
          taken = 1;
        }
      }
      for (var bracket : brackets.subList(taken, brackets.size())) {
        levels.add(new Level("point", bracket));
      }
    }
    return List.copyOf(levels);
  }

  /**
   * Returns the numbers in brackets that {@code brackets} is made of. For example, "(4)" and "(c)"
   * for "(4)(c)".
   *
   * @throws IllegalArgumentException if it is not only numbers in brackets
   */
  private static List<String> bracketed(String brackets, String words) {
    var found = new ArrayList<String>();
    var at = 0;
    while (at < brackets.length()) {
      var close = brackets.indexOf(')', at);
      if (brackets.charAt(at) != '(' || close < at + 2) {
        throw new IllegalArgumentException("Not a place: " + words);
      }
      found.add(brackets.substring(at, close + 1));
      at = close + 1;
    }
    return found;
  }

  /**
   * Returns the level of the first number in brackets after an article's number: a paragraph if it
   * starts with a digit, else a point. For example, "(4)" is paragraph 4 and "(c)" is point (c).
   */
  private static Level paragraphOrPoint(String bracket) {
    return Character.isDigit(bracket.charAt(1))
        ? new Level("paragraph", bracket.substring(1, bracket.length() - 1))
        : new Level("point", bracket);
  }

  /**
   * Returns whether the name of a place keeps the level {@code noun} {@code cited} that holds it,
   * where the next level is numbered {@code number}. It does not keep the one annex of an act, or a
   * level whose number starts the number of the next: point 6.8.1.6 is in chapter 6.
   */
  private static boolean holds(String noun, String cited, String number) {
    return !cited.isEmpty() && !number.startsWith(cited + ".") && !number.startsWith(cited + "-")
        || !noun.equals("the annex") && cited.isEmpty();
  }

  /** {@inheritDoc} It is the name of the place. */
  @Override
  public String toString() {
    return words;
  }
}
