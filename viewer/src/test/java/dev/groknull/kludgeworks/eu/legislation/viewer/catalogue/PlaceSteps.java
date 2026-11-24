// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The steps of the rules of places: which occurrences of a text the place of a change is ({@link
 * Location#in}), or the occurrence that holds it ({@link Location#around}). A change is named by
 * its act and its place as the pages name it. Each change of the act at that place must give the
 * same answer. An expected occurrence is named as a page names it too, and found in the text. A
 * change can also be named by its act and its place in Cellar's codes, to say how a page names it.
 */
public class PlaceSteps {
  private final TheAct the;

  public PlaceSteps(TheAct the) {
    this.the = the;
  }

  @Then("in the text of {day}, the change of {act} at {place} is {place}")
  public void is(LocalDate day, ActIdentifier act, Place place, Place occurrence) {
    var text = text(day);
    assertThat(each(act, place, location -> location.in(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.of(new Found(List.of(eId(text, occurrence)), false)));
  }

  @Then("in the text of {day}, the change of {act} at {place} is in {place}")
  public void within(LocalDate day, ActIdentifier act, Place place, Place occurrence) {
    var text = text(day);
    assertThat(each(act, place, location -> location.in(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.of(new Found(List.of(eId(text, occurrence)), true)));
  }

  @Then(
      "in the text of {day}, the change of {act} at {place} is each occurrence from {place} to"
          + " {place}")
  public void range(LocalDate day, ActIdentifier act, Place place, Place first, Place last) {
    var eIds = tags(day).eIds();
    var text = TextOccurrences.of(eIds);
    var from = eIds.indexOf(eId(text, first));
    var to = eIds.indexOf(eId(text, last));
    assertThat(each(act, place, location -> location.in(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.of(new Found(eIds.subList(from, to + 1), false)));
  }

  @Then("in the text of {day}, the change of {act} at {place} is no occurrence")
  public void none(LocalDate day, ActIdentifier act, Place place) {
    var text = text(day);
    assertThat(each(act, place, location -> location.in(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.empty());
  }

  @Then("in the text of {day}, the change of {act} at {place} is held by {place}")
  public void heldBy(LocalDate day, ActIdentifier act, Place place, Place holder) {
    var text = text(day);
    assertThat(each(act, place, location -> location.in(text)))
        .as("%s at %s is not in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.empty());
    assertThat(each(act, place, location -> location.around(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.of(new Found(List.of(eId(text, holder)), true)));
  }

  @Then("in the text of {day}, the change of {act} at {place} is held by no occurrence")
  public void heldByNone(LocalDate day, ActIdentifier act, Place place) {
    var text = text(day);
    assertThat(each(act, place, location -> location.around(text)))
        .as("%s at %s in the text of %s", act.cited(), place.words(), day)
        .containsOnly(Optional.empty());
  }

  @Then("the change of {act} at Cellar's place {location} is named {}")
  public void named(ActIdentifier act, Location location, String name) {
    var names =
        ActHistory.of(the.act()).changesBy(act, Language.ENG).stream()
            .filter(each -> each.location().equals(location))
            .map(each -> each.location().place().words())
            .toList();
    assertThat(names).as("the changes of %s at %s", act.cited(), location).isNotEmpty();
    assertThat(names).as("the names of %s at %s", act.cited(), location).containsOnly(name);
  }

  /** Returns what {@code find} answers for each change of {@code act} at {@code place}. */
  private List<Optional<Found>> each(
      ActIdentifier act, Place place, Function<Location, Optional<Found>> find) {
    var changes =
        ActHistory.of(the.act()).changesBy(act, Language.ENG).stream()
            .filter(each -> each.location().place().words().equals(place.words()))
            .toList();
    assertThat(changes).as("the changes of %s at %s", act.cited(), place.words()).isNotEmpty();
    return changes.stream().map(each -> find.apply(each.location())).toList();
  }

  /** Returns the eId of the occurrence that {@code place} names in {@code text}. */
  private static String eId(TextOccurrences text, Place place) {
    var found = Location.of(place).in(text);
    assertThat(found).as("%s in the text", place.words()).isPresent();
    assertThat(found.get().eIds()).as("%s in the text", place.words()).hasSize(1);
    return found.get().eIds().getFirst();
  }

  /** Returns the occurrences of the built text of the version that starts on {@code day}. */
  private TextOccurrences text(LocalDate day) {
    return TextOccurrences.of(tags(day).eIds());
  }

  /** Returns the tags of the built text of the version that starts on {@code day}. */
  private TextTags tags(LocalDate day) {
    return Objects.requireNonNull(the.texts().get(version(day)), "No built text of " + day);
  }

  /** Returns the one version of the act that starts on {@code day}. */
  private Version version(LocalDate day) {
    var found =
        the.act().timeline().versions().stream().filter(each -> each.date().equals(day)).toList();
    assertThat(found).as("the versions of %s", day).hasSize(1);
    return found.getFirst();
  }
}
