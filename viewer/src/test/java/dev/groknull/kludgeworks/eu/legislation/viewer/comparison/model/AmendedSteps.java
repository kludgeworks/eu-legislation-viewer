// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The steps of the rule of which of Cellar's amendments count in a comparison ({@link
 * dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act#amended}), in English, on the two
 * texts of the comparison ({@link Comparing}).
 */
public class AmendedSteps {
  private final Comparing comparing;

  public AmendedSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @Then("Cellar's amendments that count are those of {acts}")
  public void countingActs(List<ActIdentifier> acts) {
    assertThat(counting().stream().map(Amendment::act).collect(Collectors.toSet()))
        .isEqualTo(Set.copyOf(acts));
  }

  @Then("{act} is between the two texts, but none of its amendments count")
  public void noneOf(ActIdentifier act) {
    assertThat(comparing.act().integratedBetween(comparing.from(), comparing.to())).contains(act);
    assertThat(counting()).noneMatch(each -> each.act().equals(act));
  }

  @Then("Cellar's amendment of {act} at {place} does not count: it applies from {day}")
  public void after(ActIdentifier act, Place place, LocalDate day) {
    var at =
        comparing.act().changesBy(act, Language.ENG).stream()
            .filter(each -> each.location().place().words().equals(place.words()))
            .toList();
    assertThat(at).as("%s at %s", act.cited(), place.words()).isNotEmpty();
    assertThat(at).allSatisfy(each -> assertThat(each.from()).isEqualTo(day));
    assertThat(counting()).doesNotContainAnyElementsOf(at);
  }

  @Then("Cellar's amendment of {act} at {place} counts")
  public void counts(ActIdentifier act, Place place) {
    assertThat(counting())
        .anyMatch(
            each ->
                each.act().equals(act) && each.location().place().words().equals(place.words()));
  }

  @Then("Cellar's repeals of {act} at {place} and {place} are found on the earlier text")
  public void onTheEarlierText(ActIdentifier act, Place first, Place second) {
    var earlier = Occurrences.of(comparing.earlier()).byEId().keySet();
    var later = Occurrences.of(comparing.later()).byEId().keySet();
    for (var place : List.of(first, second)) {
      var found = placed(act, place);
      assertThat(found).as("where %s at %s is found", act.cited(), place.words()).isNotEmpty();
      assertThat(earlier).as(place.words()).containsAll(found);
      assertThat(later).as(place.words()).doesNotContainAnyElementsOf(found);
    }
  }

  @Then("Cellar's repeal of {act} at {place} is found at {place} of the later text")
  public void atTheLaterText(ActIdentifier act, Place place, Place occurrence) {
    var later = new ArrayList<String>();
    eIds(comparing.later(), later);
    var expected =
        Location.of(occurrence)
            .in(later)
            .orElseThrow(() -> new AssertionError("The later text has no " + occurrence.words()))
            .eIds();
    assertThat(placed(act, place)).containsExactlyElementsOf(expected);
  }

  /**
   * Adds the eIds of {@code element} and of the elements in it to {@code eIds}, in document order:
   * each occurrence of the text, a kept deletion too.
   */
  private static void eIds(Element element, List<String> eIds) {
    var eId = element.attribute("eId");
    if (eId != null) {
      eIds.add(eId);
    }
    element.children().forEach(child -> eIds(child, eIds));
  }

  /** Returns the occurrences at which the removals of {@code act} at {@code place} are found. */
  private List<String> placed(ActIdentifier act, Place place) {
    return amended().byOccurrence().entrySet().stream()
        .filter(
            each ->
                each.getValue().stream()
                    .anyMatch(
                        amendment ->
                            amendment.act().equals(act)
                                && amendment.role().removes()
                                && amendment.location().place().words().equals(place.words())))
        .map(java.util.Map.Entry::getKey)
        .toList();
  }

  /** Returns the amendments that count between the two texts, each one time. */
  private List<Amendment> counting() {
    return amended().byOccurrence().values().stream().flatMap(List::stream).distinct().toList();
  }

  /** Returns Cellar's amendments that count between the two texts, at their occurrences. */
  private Amended amended() {
    return comparing
        .act()
        .amended(
            comparing.from(),
            comparing.to(),
            List.copyOf(Occurrences.of(comparing.earlier()).byEId().keySet()),
            List.copyOf(Occurrences.of(comparing.later()).byEId().keySet()),
            Language.ENG);
  }
}
