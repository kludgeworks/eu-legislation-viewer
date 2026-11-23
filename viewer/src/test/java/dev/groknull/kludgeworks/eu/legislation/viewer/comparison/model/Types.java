// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Summary;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import io.cucumber.java.DataTableType;
import io.cucumber.java.ParameterType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The words of the rules that the steps of every rule share, as the domain's types: the parameter
 * types of the steps ({@code {place}}, {@code {act}}, {@code {day}}, {@code {days}}, {@code
 * {modification}}), and the rows of the tables that the features give.
 */
public class Types {
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

  /**
   * A place, as the pages name it. For example, "point 8.1.2.3(c)". It does not start with "the",
   * so that "the neighbour point 1.2.2.3(a)" is not a place.
   */
  /** An act, as the rules cite it. */
  private static final String ACT =
      "\\d{1,4}/\\d{1,4}|Directive \\d{4}/\\d+|Decision \\d{4}/\\d+|Corrigendum \\d+ to"
          + " \\d{1,4}/\\d{1,4}";

  @ParameterType("(?!the ).+")
  public Place place(String words) {
    return new Place(words);
  }

  /** A day, as the pages write it. For example, "1 October 2026". */
  @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}")
  public LocalDate day(String day) {
    return day(day, DAY);
  }

  /** Days, as the pages write them. For example, "1 January 2026 and 22 March 2026". */
  @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}(?:(?:, | and )\\d{1,2} [A-Z][a-z]+ \\d{4})*")
  public List<LocalDate> days(String days) {
    return Arrays.stream(days.split(", | and ")).map(this::day).toList();
  }

  /**
   * An act, as the rules cite it: "2015/1998", "917/2004", "Directive 2002/73", "Decision
   * 2022/2319", or "Corrigendum 1 to 2017/815".
   */
  @ParameterType(ACT)
  public ActIdentifier act(String cited) {
    return Cited.act(cited);
  }

  /** What an act did to an occurrence in one step: "replaced", "inserted", "amended" … */
  /**
   * Acts, as the pages list them: "2019/413 and 2021/255", "2017/815, 2019/103 and 2019/1583", or
   * one act alone.
   */
  @ParameterType("(?:" + ACT + ")(?:(?:, | and )(?:" + ACT + "))*")
  public List<ActIdentifier> acts(String cited) {
    return Cited.acts(cited);
  }

  @ParameterType("inserted|deleted|replaced|renumbered|amended|corrected")
  public Modification modification(String word) {
    return Modification.of(word);
  }

  /**
   * A tag as a table gives it: its act, its modification, and the place that it was taken from, if
   * the occurrence has no tag of its own. The place is found in the text of the scenario.
   */
  public record TagRow(ActIdentifier act, Modification modification, @Nullable Place takenFrom) {}

  @DataTableType
  public TagRow tag(Map<String, String> row) {
    var from = row.get("taken from");
    return new TagRow(
        Cited.act(cell(row, "act")),
        Modification.of(cell(row, "modification")),
        from == null ? null : new Place(from));
  }

  /** A step as a table gives it: the text that first includes its acts, its modification, acts. */
  public record StepRow(LocalDate textOf, Modification modification, List<ActIdentifier> acts) {
    public StepRow {
      acts = List.copyOf(acts);
    }
  }

  @DataTableType
  public StepRow step(Map<String, String> row) {
    return new StepRow(
        day(cell(row, "text of"), DAY),
        Modification.of(cell(row, "modification")),
        Cited.acts(cell(row, "acts")));
  }

  /** A day from which a part of an act applies, as a table gives it: "its entry into force" … */
  @DataTableType
  public Applying.Start start(Map<String, String> row) {
    var what = cell(row, "what");
    return new Applying.Start(
        day(cell(row, "from"), DAY), what.equals("its entry into force"), what.equals("a part"));
  }

  /**
   * A difference that a page shows, as a table gives it: its kind, its counts of inserted and
   * deleted words, and the acts that made it.
   */
  @DataTableType
  public Summary.Attributed shown(Map<String, String> row) {
    return new Summary.Attributed(
        difference(
            Kind.valueOf(cell(row, "kind").toUpperCase(Locale.ROOT)),
            Integer.parseInt(cell(row, "inserted words")),
            Integer.parseInt(cell(row, "deleted words"))),
        Attribution.madeBy(Cited.acts(cell(row, "made by"))));
  }

  /** What a legend shows, as a table gives it: yes or no for each kind of edit. */
  public record Legend(boolean insertedWords, boolean deletedWords, boolean correction) {}

  @DataTableType
  public Legend legend(Map<String, String> row) {
    return new Legend(
        yes(cell(row, "inserted words")),
        yes(cell(row, "deleted words")),
        yes(cell(row, "a correction")));
  }

  /** Returns a difference of {@code kind} with these counts of edits. */
  private static Difference difference(Kind kind, int inserted, int deleted) {
    return DifferenceBuilder.builder()
        .eId("art_1")
        .kind(kind)
        .occurrence(new Sibling("art_1", "article", "Article 1"))
        .shown(new Element("occurrence", Map.of("eId", "art_1"), List.of(), ""))
        .inserted(inserted)
        .deleted(deleted)
        .context(List.of())
        .tags(List.of())
        .earlierTags(List.of())
        .build();
  }

  private static boolean yes(String cell) {
    return switch (cell) {
      case "yes" -> true;
      case "no" -> false;
      default -> throw new IllegalArgumentException("Not yes or no: " + cell);
    };
  }

  private static LocalDate day(String day, DateTimeFormatter format) {
    return LocalDate.parse(day, format);
  }

  private static String cell(Map<String, String> row, String column) {
    return Objects.requireNonNull(row.get(column), column);
  }
}
