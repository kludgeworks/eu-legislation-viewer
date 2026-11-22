// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about the tags of an occurrence (R-WHO-02) and of a neighbour (R-SHOW-10),
 * on the consolidated versions of 2015/1998 as the content module reads them back. A step names a
 * place as the pages do, and an act by its number. The steps reach the domain only: they cannot
 * call a view or a controller, so a rule that they test is applied in the domain.
 */
public class TagsSteps {
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

  /** The number of a regulation. For example, 2015/1998. */
  private static final Pattern REGULATION = Pattern.compile("(\\d{4})/(\\d+)");

  private @Nullable Element version;
  private @Nullable Occurrences occurrences;
  private List<Tag> tags = List.of();

  /**
   * A place, as the pages name it. For example, "point 8.1.2.3(c)". It does not start with "the",
   * so that "the neighbour point 1.2.2.3(a)" is not a place.
   */
  @ParameterType("(?!the ).+")
  public Place place(String words) {
    return new Place(words);
  }

  /** A day, as the pages write it. For example, "1 October 2026". */
  @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}")
  public LocalDate day(String day) {
    return LocalDate.parse(day, DAY);
  }

  /** A regulation, by its number. For example, 2015/1998. */
  @ParameterType("\\d{4}/\\d+")
  public ActIdentifier act(String number) {
    var matched = REGULATION.matcher(number);
    if (!matched.matches()) {
      throw new IllegalArgumentException("Not the number of a regulation: " + number);
    }
    return new ActIdentifier(
        "3" + matched.group(1) + "R" + "%04d".formatted(Integer.parseInt(matched.group(2))));
  }

  @Given("the consolidated version of {act} of {day}")
  public void consolidatedVersion(ActIdentifier act, LocalDate day) {
    var celex = "0" + act.celex().substring(1) + "-" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
    var element = Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
    version = element;
    occurrences = Occurrences.of(element);
  }

  @When("the reader asks for the tags of {place}")
  public void tagsOf(Place place) {
    var text = text();
    var occurrence = text.get(eId(place));
    tags = Tags.of(Objects.requireNonNull(occurrence, place.words()), text);
  }

  @When("the reader asks for the tags of the neighbour {place}")
  public void tagsOfTheNeighbour(Place place) {
    var eId = eId(place);
    var neighbour =
        Neighbours.of(Objects.requireNonNull(version, "no consolidated version given"), Set.of(eId))
            .get(eId);
    tags = Objects.requireNonNull(neighbour, place.words()).tags();
  }

  @Then("the tags are")
  public void theTagsAre(List<Map<String, String>> expected) {
    assertThat(tags)
        .containsExactlyElementsOf(
            expected.stream()
                .map(
                    row -> {
                      var from = row.get("taken from");
                      return new Tag(
                          act(Objects.requireNonNull(row.get("act"))).celex(),
                          Modification.of(Objects.requireNonNull(row.get("modification"))),
                          from == null ? null : eId(new Place(from)));
                    })
                .toList());
  }

  /** Returns the eId of the one occurrence of the text that is {@code place}. */
  private String eId(Place place) {
    var found =
        Location.of(place)
            .in(List.copyOf(text().byEId().keySet()))
            .orElseThrow(() -> new AssertionError("The text has no " + place));
    assertThat(found.eIds()).as(place.words()).hasSize(1);
    assertThat(found.within()).as(place.words()).isFalse();
    return found.eIds().getFirst();
  }

  private Occurrences text() {
    return Objects.requireNonNull(occurrences, "no consolidated version given");
  }
}
