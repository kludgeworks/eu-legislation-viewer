// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.List;
import java.util.Optional;

/**
 * The feature "Search": one function for each of its rules. A rule reads facts and gives one new
 * fact. It does not call another rule: {@link Citation#read} and the search of the catalogue
 * ({@link Searches#identified}) apply the rules.
 */
public final class Search {

  private Search() {}

  /**
   * A citation goes to an act only if it names one act. The acts that a citation can name are the
   * acts whose year and number are its two numbers, in either order. Of these acts, the citation
   * names only the acts of its kind and its subtype, if the reader typed them. If it names exactly
   * one act, the search goes to that act. If it names two or more acts, or none, the search does
   * not guess: the reader gets the list.
   *
   * <p>For example, "185/2010" can name Regulation 185/2010 and Decision 2010/185, so the reader
   * gets the list. "Decision 2010/185/EU" names one act.
   *
   * @param citation the citation that the reader typed
   * @param candidates the acts whose year and number are the two numbers of the citation
   * @return the one act that the citation names. Empty if it names none, or more than one.
   */
  @BusinessRule(group = RuleGroup.SEARCH, name = "a-citation-goes-to-one-act")
  public static Optional<ActIdentifier> aCitationGoesToOneAct(
      Citation citation, List<Citation.Candidate> candidates) {
    var named =
        candidates.stream()
            .filter(each ->
                citation.kind().isEmpty()
                    || each.act().celex().substring(5, 6).equals(citation.kind()))
            .filter(each ->
                citation.subtype().isEmpty()
                    || each.type() != null && each.type().code().endsWith("_" + citation.subtype()))
            .map(Citation.Candidate::act)
            .distinct()
            .toList();
    return named.size() == 1 ? Optional.of(named.getFirst()) : Optional.empty();
  }

  /**
   * An act from before 2000 can cite its year with two digits, as Directive 95/46/EC does. The
   * first number is the year 19xx if these conditions are true:
   *
   * <ul>
   *   <li>The citation names the kind of act.
   *   <li>The first number has two digits, and the second number has fewer than four digits.
   *   <li>The first number is 50 or more.
   * </ul>
   *
   * @param namesTheKind whether the citation names the kind of act
   * @param first the first number, as the reader typed it
   * @param second the second number, as the reader typed it
   * @return the first number: the year 19xx, or the number as the reader typed it
   */
  @BusinessRule(group = RuleGroup.SEARCH, name = "a-year-of-two-digits")
  public static int aYearOfTwoDigits(boolean namesTheKind, String first, String second) {
    var number = Integer.parseInt(first);
    return namesTheKind && first.length() == 2 && second.length() < 4 && number >= 50
        ? number + 1900
        : number;
  }
}
