// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Summary;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about who made a difference (R-WHO-10) and what the page's summary and
 * legend say (R-SHOW-06, R-SHOW-07, R-SHOW-17). An act is cited as the pages cite it: "2026/449",
 * "Corrigendum 1 to 2017/815". The rows of the tables are the domain's ({@link Types}). The steps
 * reach the domain only.
 */
public class AttributionSteps {
  private Attribution attribution = Attribution.NONE;
  private @Nullable Summary summary;

  @Given("a difference made by {string}")
  public void aDifferenceMadeBy(String acts) {
    attribution = Attribution.madeBy(Cited.acts(acts));
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
  public void thePageShows(List<Summary.Attributed> shown) {
    summary = Summary.of(shown);
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
  public void theLegendShows(List<Types.Legend> expected) {
    assertThat(expected).as("one row").hasSize(1);
    var summary = summary();
    assertThat(new Types.Legend(summary.inserted(), summary.deleted(), summary.corrected()))
        .isEqualTo(expected.getFirst());
  }

  private Summary summary() {
    return Objects.requireNonNull(summary, "no differences given");
  }
}
