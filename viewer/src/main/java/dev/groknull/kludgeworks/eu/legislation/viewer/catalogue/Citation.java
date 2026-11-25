// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * The citation of an act that a reader types in the search: its year and its number, in either
 * order, and the kind of act if the reader names it. For example, "Regulation (EU) 2016/679",
 * "679/2016" or "Directive 95/46/EC". The search goes straight to the act that it names, if it
 * names one act.
 *
 * @param first the number before the slash. A year of two digits is the full year ({@link #read}).
 * @param second the number after the slash
 * @param kind the kind of act that it names, as the letter of its CELEX: R for a regulation, L for
 *     a directive, D for a decision. Empty if it names no kind.
 * @param subtype IMPL for an implementing act, DEL for a delegated act. Empty if it names neither.
 */
@ValueObject
public record Citation(int first, int second, String kind, String subtype) {

  private static final Pattern CITATION =
      Pattern.compile(
          "(?:(?:(implementing|delegated)\\s+)?(regulation|directive|decision)\\s+"
              + "(?:\\((?:eu|ec|eec)\\)\\s+)?(?:no\\s+)?)?"
              + "(\\d{1,4})\\s*/\\s*(\\d{1,4})(?:/(?:eu|ec|eec))?",
          Pattern.CASE_INSENSITIVE);

  /**
   * Returns the citation that {@code typed} is, or empty if it is none. An act from before 2000 can
   * cite its year with two digits, as Directive 95/46/EC does. If the citation names the kind of
   * act, its first number has two digits, its second number has fewer than four, and the first
   * number is 50 or more, the first number is the year 19xx.
   */
  @BusinessRule(group = RuleGroup.SEARCH, name = "a-year-of-two-digits")
  public static Optional<Citation> read(String typed) {
    var citation = CITATION.matcher(typed.strip());
    if (!citation.matches()) {
      return Optional.empty();
    }
    var kind =
        switch (Objects.toString(citation.group(2), "").toLowerCase(Locale.ROOT)) {
          case "regulation" -> "R";
          case "directive" -> "L";
          case "decision" -> "D";
          default -> "";
        };
    var subtype =
        switch (Objects.toString(citation.group(1), "").toLowerCase(Locale.ROOT)) {
          case "implementing" -> "IMPL";
          case "delegated" -> "DEL";
          default -> "";
        };
    var typedFirst = citation.group(3);
    var typedSecond = citation.group(4);
    var first = Integer.parseInt(typedFirst);
    if (!kind.isEmpty() && typedFirst.length() == 2 && typedSecond.length() < 4 && first >= 50) {
      first += 1900;
    }
    return Optional.of(new Citation(first, Integer.parseInt(typedSecond), kind, subtype));
  }

  /**
   * Returns the act that this citation names, if it names one. {@code candidates} are the acts
   * whose year and number are the two numbers of the citation, in either order. Of them, the
   * citation names those of its kind and subtype, if it names them. If it names exactly one act,
   * the search goes to it. If it names more, or none, the reader gets the list that the search
   * finds.
   */
  @BusinessRule(group = RuleGroup.SEARCH, name = "a-citation-goes-to-one-act")
  public Optional<ActIdentifier> names(List<Candidate> candidates) {
    var named =
        candidates.stream()
            .filter(each -> kind.isEmpty() || each.act().celex().substring(5, 6).equals(kind))
            .filter(each ->
                subtype.isEmpty()
                    || each.type() != null && each.type().code().endsWith("_" + subtype))
            .map(Candidate::act)
            .distinct()
            .toList();
    return named.size() == 1 ? Optional.of(named.getFirst()) : Optional.empty();
  }

  /**
   * An act that a citation can name: one whose year and number are its two numbers.
   *
   * @param act the act
   * @param type its resource type, for example REG_IMPL. Null if the catalogue has none.
   */
  @ValueObject
  public record Candidate(ActIdentifier act, @Nullable ResourceType type) {}
}
