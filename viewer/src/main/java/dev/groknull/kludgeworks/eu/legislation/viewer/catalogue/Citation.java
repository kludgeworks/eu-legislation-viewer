// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

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
   * Returns the citation that {@code typed} is, or empty if it is none. Its first number can be a
   * year of two digits ({@link Search#aYearOfTwoDigits}).
   */
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
    return Optional.of(
        new Citation(
            Search.aYearOfTwoDigits(!kind.isEmpty(), typedFirst, typedSecond),
            Integer.parseInt(typedSecond),
            kind,
            subtype));
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
