// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.DataTableType;
import io.cucumber.java.ParameterType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The words of the catalogue's rules, as the domain's types: the parameter types of the steps
 * ({@code {act}}, {@code {acts}}, {@code {day}}, {@code {days}}, {@code {language}}, {@code
 * {place}}, {@code {state}}).
 */
public class CatalogueTypes {
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

  /** An act, as the rules cite it: "2015/1998", "Directive 2022/2556" … ({@link Cited}). */
  @ParameterType("\\d{1,4}/\\d{1,4}|Directive \\d{4}/\\d+|Decision \\d{4}/\\d+")
  public ActIdentifier act(String cited) {
    return Cited.act(cited);
  }

  /**
   * Acts, as the rules cite them, separated by commas. For example, "Corrigendum 2 to 2015/1998,
   * 2015/2426".
   */
  @ParameterType(
      "(?:Corrigendum \\d+ to )?\\d{1,4}/\\d{1,4}(?:, (?:Corrigendum \\d+ to )?\\d{1,4}/\\d{1,4})*")
  public List<ActIdentifier> acts(String cited) {
    return Arrays.stream(cited.split(", ")).map(Cited::act).toList();
  }

  /** A day, as the pages write it. For example, "1 October 2026". */
  @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}")
  public LocalDate day(String day) {
    return LocalDate.parse(day, DAY);
  }

  /**
   * Days, as the pages write them. For example, "29 December 2020, 28 June 2021 and 1 January
   * 2022".
   */
  @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}(?:(?:, | and )\\d{1,2} [A-Z][a-z]+ \\d{4})*")
  public List<LocalDate> days(String days) {
    return Arrays.stream(days.split(", | and ")).map(this::day).toList();
  }

  /**
   * A place, as the pages name it, in EUR-Lex's words. For example, "article 79 paragraph 7" or
   * "annex appendix 8-B indent 1 point (b)". The words that follow it in the step end it.
   */
  @ParameterType("(?:annex|article|appendix|chapter|part|point|section|title)(?: \\S+)*?")
  public Place place(String words) {
    return new Place(words);
  }

  /**
   * A place in Cellar's codes, as Cellar writes it without the URIs of its codes. For example, "AN
   * PO 1.3.1.1 PTA (g)" or "AR 114 PA 1 ALN 1".
   */
  @ParameterType("[A-Z]{1,8}(?: (?:[A-Z]{1,8}|[0-9(][^ ]*))*")
  public Location location(String code) {
    return new Location(code);
  }

  /** The state of an act, or of one change, on a day, in the words of the rules. */
  @ParameterType(
      "is not in force|is no longer in force|is in force and does not apply yet|applies in"
          + " part|applies|has a state that is not known")
  public State state(String words) {
    return switch (words) {
      case "is not in force" -> State.NOT_IN_FORCE;
      case "is no longer in force" -> State.NO_LONGER_IN_FORCE;
      case "is in force and does not apply yet" -> State.NOT_APPLYING;
      case "applies in part" -> State.IN_PART;
      case "applies" -> State.APPLIES;
      case "has a state that is not known" -> State.NOT_KNOWN;
      default -> throw new IllegalArgumentException("Not a state: " + words);
    };
  }

  /** A language, by its name in English. For example, "German". */
  @ParameterType("English|German|Swedish|Polish|Croatian")
  public Language language(String name) {
    return switch (name) {
      case "English" -> Language.ENG;
      case "German" -> Language.DEU;
      case "Swedish" -> Language.SWE;
      case "Polish" -> Language.POL;
      case "Croatian" -> Language.HRV;
      default -> throw new IllegalArgumentException("Not a language of the rules: " + name);
    };
  }

  /** A day from which a part of an act applies, as a table gives it: "its entry into force" … */
  @DataTableType
  public Applying.Start start(Map<String, String> row) {
    var what = Objects.requireNonNull(row.get("what"), "what");
    return new Applying.Start(
        day(Objects.requireNonNull(row.get("from"), "from")),
        what.equals("its entry into force"),
        what.equals("a part"));
  }
}
