// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The steps of the rules of the text that a comparison shows: the version whose text a comparison
 * shows for a day ({@link LawOnADay#shown}), and the occurrences of a text with a change that does
 * not apply on a day yet ({@link LawOverTime#changesThatDoNotApplyYet}, {@link Prospective#at}).
 * They read the act of the scenario and the texts of its versions that are built ({@link TheAct}),
 * in English.
 */
public class ComparedTextSteps {
  private final TheAct the;

  public ComparedTextSteps(TheAct the) {
    this.the = the;
  }

  @Then("for {day}, a comparison shows the text of {day}")
  public void shows(LocalDate day, LocalDate version) {
    assertThat(
            Optional.ofNullable(
                    LawOverTime.of(the.act(), Language.ENG, the.texts()).on(day).shown())
                .map(Version::date))
        .as("the text that a comparison shows for %s", day)
        .contains(version);
  }

  @Then("for {day}, a comparison shows no text")
  public void showsNone(LocalDate day) {
    assertThat(
            Optional.ofNullable(
                LawOverTime.of(the.act(), Language.ENG, the.texts()).on(day).shown()))
        .as("the text that a comparison shows for %s", day)
        .isEmpty();
  }

  @Then("on {day}, the text of {day} has a change that does not apply yet at {int} occurrences")
  public void count(LocalDate day, LocalDate version, int occurrences) {
    assertThat(prospective(day, version).byOccurrence()).hasSize(occurrences);
  }

  @Then("on {day}, the text of {day} has no change that does not apply yet")
  public void none(LocalDate day, LocalDate version) {
    assertThat(prospective(day, version).byOccurrence()).isEmpty();
  }

  @Then("on {day}, in the text of {day}, {place} has a change of {act} that does not apply yet")
  public void at(LocalDate day, LocalDate version, Place place, ActIdentifier act) {
    assertThat(prospective(day, version).at(eId(version, place)))
        .as("%s on %s", place.words(), day)
        .contains(act);
  }

  @Then("on {day}, in the text of {day}, {place} has no change that does not apply yet")
  public void notAt(LocalDate day, LocalDate version, Place place) {
    assertThat(prospective(day, version).at(eId(version, place)))
        .as("%s on %s", place.words(), day)
        .isEmpty();
  }

  private Prospective prospective(LocalDate day, LocalDate version) {
    return LawOverTime.of(the.act(), Language.ENG, the.texts())
        .changesThatDoNotApplyYet(version(version), day);
  }

  /** Returns the eId of the occurrence that {@code place} names in the text of {@code version}. */
  private String eId(LocalDate version, Place place) {
    var found = PlaceWords.location(place).in(TextOccurrences.of(tags(version).eIds()));
    assertThat(found).as("%s in the text of %s", place.words(), version).isPresent();
    assertThat(found.get().eIds()).as("%s in the text of %s", place.words(), version).hasSize(1);
    return found.get().eIds().getFirst();
  }

  private TextTags tags(LocalDate version) {
    return Objects.requireNonNull(
        the.texts().get(version(version)), "No built text of the version of " + version);
  }

  private Version version(LocalDate day) {
    var found =
        the.act().timeline().versions().stream().filter(each -> each.date().equals(day)).toList();
    assertThat(found).as("the versions of %s", day).hasSize(1);
    return found.getFirst();
  }
}
