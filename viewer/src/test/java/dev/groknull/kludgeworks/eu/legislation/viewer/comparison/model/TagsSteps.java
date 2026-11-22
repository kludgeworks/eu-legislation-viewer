// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about the tags of an occurrence (R-WHO-02) and of a neighbour (R-SHOW-10),
 * on the consolidated versions of 2015/1998 as the content module reads them back. A step names a
 * place as the pages do, and an act by its number ({@link Types}). The steps reach the domain only:
 * they cannot call a view or a controller, so a rule that they test is applied in the domain.
 */
public class TagsSteps {
  private @Nullable Element version;
  private @Nullable Occurrences occurrences;
  private List<Tag> tags = List.of();

  @Given("the consolidated version of {act} of {day}")
  public void consolidatedVersion(ActIdentifier act, LocalDate day) {
    var element = Comparing.consolidated(act, day);
    version = element;
    occurrences = Occurrences.of(element);
  }

  @When("the reader asks for the tags of {place}")
  public void tagsOf(Place place) {
    var text = text();
    var occurrence = text.get(Comparing.eId(place, text));
    tags = Tags.of(Objects.requireNonNull(occurrence, place.words()), text);
  }

  @When("the reader asks for the tags of the neighbour {place}")
  public void tagsOfTheNeighbour(Place place) {
    var eId = Comparing.eId(place, text());
    var neighbour =
        Neighbours.of(Objects.requireNonNull(version, "no consolidated version given"), Set.of(eId))
            .get(eId);
    tags = Objects.requireNonNull(neighbour, place.words()).tags();
  }

  @Then("the tags are")
  public void theTagsAre(List<Types.TagRow> expected) {
    var text = text();
    assertThat(tags)
        .containsExactlyElementsOf(
            expected.stream()
                .map(
                    row ->
                        new Tag(
                            row.act().celex(),
                            row.modification(),
                            row.takenFrom() == null
                                ? null
                                : Comparing.eId(Objects.requireNonNull(row.takenFrom()), text)))
                .toList());
  }

  private Occurrences text() {
    return Objects.requireNonNull(occurrences, "no consolidated version given");
  }
}
