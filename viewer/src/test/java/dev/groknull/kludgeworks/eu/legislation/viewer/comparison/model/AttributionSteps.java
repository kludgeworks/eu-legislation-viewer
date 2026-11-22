// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Summary;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about who made a difference (R-WHO-10) and what the page's summary and
 * legend say (R-SHOW-06, R-SHOW-07, R-SHOW-17). An act is cited as the pages cite it: "2026/449",
 * "Corrigendum 1 to 2017/815". The steps reach the domain only.
 */
public class AttributionSteps {
  private Attribution attribution = Attribution.NONE;
  private @Nullable Summary summary;

  @Given("a difference made by {string}")
  public void aDifferenceMadeBy(String acts) {
    attribution = attribution(acts);
  }

  @Then("the difference is a correction")
  public void isACorrection() {
    assertThat(attribution.corrected()).isTrue();
  }

  @Then("the difference is not a correction")
  public void isNotACorrection() {
    assertThat(attribution.corrected()).isFalse();
  }

  @Given("the page shows these differences")
  public void thePageShows(List<Map<String, String>> rows) {
    summary =
        Summary.of(
            rows.stream()
                .map(
                    row ->
                        new Summary.Attributed(
                            difference(
                                Kind.valueOf(cell(row, "kind").toUpperCase(Locale.ROOT)),
                                Integer.parseInt(cell(row, "inserted words")),
                                Integer.parseInt(cell(row, "deleted words"))),
                            attribution(cell(row, "made by"))))
                .toList());
  }

  @Then("the summary names {act}")
  public void theSummaryNames(ActIdentifier act) {
    assertThat(summary().by()).isEqualTo(act);
  }

  @Then("the summary names no act")
  public void theSummaryNamesNoAct() {
    assertThat(summary().by()).isNull();
  }

  @Then("the legend shows")
  public void theLegendShows(List<Map<String, String>> expected) {
    var row = expected.getFirst();
    var summary = summary();
    assertThat(List.of(summary.inserted(), summary.deleted(), summary.corrected()))
        .containsExactly(
            yes(cell(row, "inserted words")),
            yes(cell(row, "deleted words")),
            yes(cell(row, "a correction")));
  }

  private static Attribution attribution(String acts) {
    return Attribution.madeBy(Cited.acts(acts));
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

  private static String cell(Map<String, String> row, String column) {
    return Objects.requireNonNull(row.get(column), column);
  }

  private Summary summary() {
    return Objects.requireNonNull(summary, "no differences given");
  }
}
