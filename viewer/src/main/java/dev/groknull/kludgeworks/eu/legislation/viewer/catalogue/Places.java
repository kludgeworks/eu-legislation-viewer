// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextOccurrences.Walk;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The feature "Places": one function for each of its rules. A rule reads facts and gives one new
 * fact. It does not call another rule: {@link Location} applies the rules. The facts are how far
 * the codes of a location go in a text ({@link TextOccurrences#walk}). The rule
 * a-page-names-a-place-as-eur-lex-does is {@link Place#named}.
 */
public final class Places {

  private Places() {}

  /**
   * A place is the occurrence of the text that it names. A part of an occurrence that the text does
   * not number, such as a subparagraph or a sentence, is in the occurrence that holds it. A range
   * of points is each occurrence from the first point to the last.
   *
   * <p>The viewer does not guess. A place that finds no occurrence, or that finds several
   * occurrences where it names one, finds none.
   *
   * @param walk how far the place goes in the text
   */
  @BusinessRule(
      group = RuleGroup.PLACES,
      name = "a-place-is-the-occurrences-it-names",
      revision = 2)
  public static Optional<Found> aPlaceIsTheOccurrencesItNames(Walk walk) {
    return walk instanceof Walk.Reached reached
        ? Optional.of(new Found(reached.eIds(), reached.within()))
        : Optional.empty();
  }

  /**
   * A place that the text does not have is in the occurrence that holds it. For example, a point
   * 4.1.3.4 is in point 4.1.3.
   *
   * <p>An act can insert an occurrence after the occurrence of the same number, with a letter after
   * the number. Such an occurrence is at the occurrence of the number alone. For example, an
   * Article 18a is at Article 18.
   *
   * <p>If the text has no such occurrence, the place is in no occurrence. The occurrence that holds
   * the place is the nearest one: the point that holds a point, else the occurrence of the codes
   * before it.
   *
   * @param walk how far the place goes in the text
   * @param text the occurrences of the text
   */
  @BusinessRule(group = RuleGroup.PLACES, name = "a-place-the-text-does-not-have")
  public static Optional<Found> aPlaceTheTextDoesNotHave(Walk walk, TextOccurrences text) {
    if (!(walk instanceof Walk.Missing missing)) {
      return Optional.empty();
    }
    var number = missing.number();
    List<String> found = List.of();
    if (number != null) {
      for (var upper = upper(number); upper != null && found.isEmpty(); upper = upper(upper)) {
        found = text.numbered(missing, upper);
      }
      var alone = withoutLetters(number);
      if (found.isEmpty() && !alone.equals(number)) {
        found = text.numbered(missing, alone);
      }
    }
    var holder = found.size() == 1 ? found.getFirst() : missing.at();
    return holder.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(holder), true));
  }

  /**
   * Returns the number of the point that holds the point numbered {@code number}, or null if the
   * number has no dot. For example, 4.1.3 for 4.1.3.4.
   */
  private static @Nullable String upper(String number) {
    var dot = number.lastIndexOf('.');
    return dot > 0 ? number.substring(0, dot) : null;
  }

  /**
   * Returns {@code number} without the letters after it. For example, 18 for 18a. Returns {@code
   * number} as it is if it does not start with a digit, or if it has no letter after it.
   */
  private static String withoutLetters(String number) {
    var end = number.length();
    while (end > 0 && Character.isLetter(number.charAt(end - 1))) {
      end--;
    }
    var digits = number.substring(0, end);
    return end > 0 && digits.chars().allMatch(Character::isDigit) ? digits : number;
  }
}
