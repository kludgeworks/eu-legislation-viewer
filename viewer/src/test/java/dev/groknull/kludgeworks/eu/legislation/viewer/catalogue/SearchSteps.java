// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The steps of the rules of the search: how a typed citation reads ({@link Citation#read}), and the
 * act that it names ({@link Citation#names}). The acts that it can name are given as the catalogue
 * has them, each with its resource type: the acts whose year and number are its two numbers.
 */
public class SearchSteps {
  private List<Citation.Candidate> candidates = List.of();
  private Optional<Citation> citation = Optional.empty();

  @Given("these acts have the year and the number of the citation:")
  public void candidates(List<Map<String, String>> rows) {
    candidates =
        rows.stream()
            .map(row ->
                new Citation.Candidate(
                    Cited.act(Objects.requireNonNull(row.get("act"))),
                    new ResourceType(Objects.requireNonNull(row.get("type")))))
            .toList();
  }

  @When("the reader types {string}")
  public void types(String typed) {
    citation = Citation.read(typed);
  }

  @Then("the search goes to {act}")
  public void goesTo(ActIdentifier act) {
    assertThat(citation().names(candidates)).contains(act);
  }

  @Then("the reader gets the list")
  public void getsTheList() {
    assertThat(citation().names(candidates)).isEmpty();
  }

  @Then("its numbers are {int} and {int}")
  public void numbers(int first, int second) {
    assertThat(citation().first()).as("the first number").isEqualTo(first);
    assertThat(citation().second()).as("the second number").isEqualTo(second);
  }

  @Then("it is no citation")
  public void none() {
    assertThat(citation).isEmpty();
  }

  private Citation citation() {
    return citation.orElseThrow(() -> new AssertionError("The reader typed no citation"));
  }
}
