// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The steps of the rules of the history of an act: its amending acts and their order ({@link
 * Act#amendingActs}), the changes of an amending act ({@link Act#changesInTextOrder}), the
 * corrigenda ({@link Act#corrigendaOf}), the two texts and the occurrences of a change ({@link
 * Act#integrating}, {@link Act#occurrences}), the other acts of a version ({@link
 * Act#integratedWith}), the history of a provision ({@link Act#historyOf}), the occurrences that
 * the data names no act for ({@link Act#unattributed}), the amending acts between two days ({@link
 * Act#amendingActsBetween}) and the days of change ({@link Act#changeDays}). They read the act of
 * the scenario and the texts of its versions that are built ({@link TheAct}), in English unless a
 * step names the language.
 */
public class HistorySteps {
  private final TheAct the;
  private ProvisionHistory history = new ProvisionHistory("", List.of(), List.of());

  public HistorySteps(TheAct the) {
    this.the = the;
  }

  @Then("the amending acts of the act in {language} are, newest first, {acts}")
  public void amendingActs(Language language, List<ActIdentifier> acts) {
    assertThat(the.act().amendingActs(language)).containsExactlyElementsOf(acts);
  }

  @Then("{act} is an amending act of the act in {language}")
  public void amending(ActIdentifier act, Language language) {
    assertThat(the.act().amendingActs(language)).contains(act);
  }

  @Then("{act} is not an amending act of the act in {language}")
  public void notAmending(ActIdentifier act, Language language) {
    assertThat(the.act().amendingActs(language)).doesNotContain(act);
  }

  @Then("no version of the act includes {act}")
  public void inNoVersion(ActIdentifier act) {
    assertThat(the.act().timeline().versions())
        .noneMatch(version -> version.integrates().contains(act));
  }

  @Then("the first day of {act} is {day}")
  public void firstDay(ActIdentifier act, LocalDate day) {
    assertThat(the.act().firstDay(act, Language.ENG)).contains(day);
  }

  @Then("{act} is newer than {act} in the history of the act in {language}")
  public void newer(ActIdentifier newer, ActIdentifier older, Language language) {
    var acts = the.act().amendingActs(language);
    assertThat(acts).contains(newer, older);
    assertThat(acts.indexOf(newer)).isLessThan(acts.indexOf(older));
  }

  @Then("the latest text that is built is the text of {day}")
  public void latest(LocalDate day) {
    assertThat(the.act().latestText(Language.ENG, the.texts())).contains(text(day));
  }

  @Then("{act} has {int} changes, and the first three, in the order of the text, are at:")
  public void firstInTextOrder(ActIdentifier act, int count, List<String> places) {
    var latest = the.act().latestText(Language.ENG, the.texts()).orElseThrow();
    var changes = the.act().changesInTextOrder(act, Language.ENG, latest);
    assertThat(changes).hasSize(count);
    assertThat(words(changes).subList(0, places.size())).containsExactlyElementsOf(places);
  }

  @Then("{act} has {int} changes, and the first three, in the order of the data, are at:")
  public void firstInDataOrder(ActIdentifier act, int count, List<String> places) {
    assertThat(the.act().latestText(Language.ENG, the.texts())).isEmpty();
    var changes = the.act().changesInTextOrder(act, Language.ENG, null);
    assertThat(changes).hasSize(count);
    assertThat(words(changes).subList(0, places.size())).containsExactlyElementsOf(places);
  }

  @Then("{act} has no changes in {language}")
  public void noChanges(ActIdentifier act, Language language) {
    assertThat(the.act().changesBy(act, language)).isEmpty();
  }

  @Then("{act} has changes in {language}")
  public void changes(ActIdentifier act, Language language) {
    assertThat(the.act().changesBy(act, language)).isNotEmpty();
  }

  @Then("in the order of the text, these changes of {act} follow one another:")
  public void followOneAnother(ActIdentifier act, List<String> places) {
    var latest = the.act().latestText(Language.ENG, the.texts()).orElseThrow();
    var words = words(the.act().changesInTextOrder(act, Language.ENG, latest));
    assertThat(Collections.indexOfSubList(words, places))
        .as("%s in the order of the text: %s", places, words)
        .isNotNegative();
  }

  @Then("the text of {day} does not have {place}, {place} or {place}, and they are in {place}")
  public void heldBy(LocalDate day, Place first, Place second, Place third, Place holder) {
    var occurrences = Location.occurrences(text(day).eIds());
    var held = Location.of(holder).in(occurrences).orElseThrow().eIds();
    for (var place : List.of(first, second, third)) {
      var location = Location.of(place);
      assertThat(location.in(occurrences)).as(place.words()).isEmpty();
      assertThat(location.around(occurrences).orElseThrow().eIds())
          .as(place.words())
          .isEqualTo(held);
    }
  }

  @Then("the corrigenda of {act} in {language} are {acts}")
  public void corrigenda(ActIdentifier act, Language language, List<ActIdentifier> corrigenda) {
    assertThat(the.act().corrigendaOf(act, language)).containsExactlyElementsOf(corrigenda);
  }

  @Then("the corrigenda of {act} in {language} are none")
  public void noCorrigenda(ActIdentifier act, Language language) {
    assertThat(the.act().corrigendaOf(act, language)).isEmpty();
  }

  @Then("the changes of {act} show between the texts of {day} and of {day}")
  public void twoTexts(ActIdentifier act, LocalDate earlier, LocalDate later) {
    assertThat(the.act().beforeIntegrating(act, Language.ENG).map(Version::date)).contains(earlier);
    assertThat(the.act().integrating(act, Language.ENG).map(Version::date)).contains(later);
  }

  @Then("no texts show the changes of {act}")
  public void noTexts(ActIdentifier act) {
    assertThat(the.act().integrating(act, Language.ENG)).isEmpty();
    assertThat(the.act().beforeIntegrating(act, Language.ENG)).isEmpty();
  }

  @Then("the occurrences of the change of {act} at {place} are {place} of the later text")
  public void occurrences(ActIdentifier act, Place place, Place occurrence) {
    var later = text(the.act().integrating(act, Language.ENG).orElseThrow().date()).eIds();
    var earlier = text(the.act().beforeIntegrating(act, Language.ENG).orElseThrow().date()).eIds();
    var changes =
        the.act().changesBy(act, Language.ENG).stream()
            .filter(each -> each.location().place().words().equals(place.words()))
            .toList();
    assertThat(changes).as("%s at %s", act.cited(), place.words()).hasSize(1);
    var found = Act.occurrences(changes.getFirst(), later, earlier);
    var expected =
        Location.of(occurrence)
            .in(Location.occurrences(later))
            .orElseThrow(
                () ->
                    new AssertionError("No " + occurrence.words() + " in the later text: " + found))
            .eIds();
    assertThat(found).isEqualTo(expected);
  }

  @Then("the other acts of the first version with {act} are {acts}")
  public void others(ActIdentifier act, List<ActIdentifier> others) {
    assertThat(the.act().integratedWith(act, Language.ENG)).containsExactlyElementsOf(others);
  }

  @Then("the other acts of the first version with {act} are none")
  public void noOthers(ActIdentifier act) {
    assertThat(the.act().integratedWith(act, Language.ENG)).isEmpty();
  }

  @Then("the first version with {act} is the version of {day}")
  public void firstVersion(ActIdentifier act, LocalDate day) {
    assertThat(the.act().integrating(act, Language.ENG).map(Version::date)).contains(day);
  }

  @Then("in the text of {day}, the acts that changed {place} are, newest first, {acts}")
  public void history(LocalDate day, Place place, List<ActIdentifier> acts) {
    history = historyOf(day, place);
    assertThat(history.acts()).containsExactlyElementsOf(acts);
  }

  @Then("in the text of {day}, the acts that changed {place} include {act}")
  public void historyIncludes(LocalDate day, Place place, ActIdentifier act) {
    history = historyOf(day, place);
    assertThat(history.acts()).contains(act);
  }

  @Then("in the text of {day}, {act} did not change {place}")
  public void notInHistory(LocalDate day, ActIdentifier act, Place place) {
    assertThat(historyOf(day, place).acts()).doesNotContain(act);
  }

  @Then("{act} changed it at {place}")
  public void changedAt(ActIdentifier act, Place place) {
    assertThat(history.changes())
        .anyMatch(
            each ->
                each.act().equals(act) && each.location().place().words().equals(place.words()));
  }

  @Then("the text tags it with {act}, which Cellar names no change of there")
  public void tagging(ActIdentifier act) {
    assertThat(history.tagging()).contains(act);
    assertThat(history.changes()).noneMatch(each -> each.act().equals(act));
  }

  @Then("in the text of {day}, the data names no act for {place} and {place}")
  public void unattributed(LocalDate day, Place first, Place second) {
    var occurrences = Location.occurrences(text(day).eIds());
    var expected =
        List.of(first, second).stream()
            .map(place -> Location.of(place).in(occurrences).orElseThrow().eIds().getFirst())
            .toList();
    assertThat(the.act().unattributed(version(day), Language.ENG, the.texts()))
        .containsExactlyElementsOf(expected);
  }

  @Then("in the text of {day}, the data names an act for each occurrence")
  public void noneUnattributed(LocalDate day) {
    assertThat(the.act().unattributed(version(day), Language.ENG, the.texts())).isEmpty();
  }

  @Then("the first text with {place} is the text of {day}")
  public void firstWith(Place place, LocalDate day) {
    var eId =
        Location.of(place)
            .in(Location.occurrences(text(day).eIds()))
            .orElseThrow()
            .eIds()
            .getFirst();
    assertThat(the.act().firstWith(eId, Language.ENG, the.texts()).map(Version::date))
        .contains(day);
  }

  @Then("the amending acts between {day} and {day} are {acts}")
  public void between(LocalDate from, LocalDate to, List<ActIdentifier> acts) {
    assertThat(the.act().amendingActsBetween(from, to, Language.ENG, the.texts())).contains(acts);
  }

  @Then("the act has no amending acts between {day} and {day}")
  public void noneBetween(LocalDate from, LocalDate to) {
    assertThat(the.act().amendingActsBetween(from, to, Language.ENG, the.texts())).isEmpty();
  }

  @Then(
      "of the amending acts between {day} and {day}, those that changed {place} in the text of"
          + " {day} are {acts}")
  public void betweenForAProvision(
      LocalDate from, LocalDate to, Place place, LocalDate day, List<ActIdentifier> acts) {
    var between = the.act().amendingActsBetween(from, to, Language.ENG, the.texts()).orElseThrow();
    assertThat(historyOf(day, place).actsAmong(between)).containsExactlyElementsOf(acts);
  }

  @Then("the act changes on {int} days")
  public void changeDays(int count) {
    assertThat(the.act().changeDays(Language.ENG)).hasSize(count);
  }

  @Then("on {day}, the act is changed by {acts}")
  public void changedOn(LocalDate day, List<ActIdentifier> acts) {
    assertThat(the.act().changeDays(Language.ENG).get(day)).containsExactlyElementsOf(acts);
  }

  @Then("{act} changes the act on {days}")
  public void daysOf(ActIdentifier act, List<LocalDate> days) {
    assertThat(daysOf(act)).containsExactlyElementsOf(days);
  }

  @Then("{act} changes the act on no day")
  public void noDay(ActIdentifier act) {
    assertThat(daysOf(act)).isEmpty();
  }

  @Then("the entry into force of {act} is {day}")
  public void entryIntoForce(ActIdentifier act, LocalDate day) {
    assertThat(the.act().entryIntoForce(act)).contains(day);
  }

  /** Returns the days on which {@code act} changes the act of the scenario, in order. */
  private List<LocalDate> daysOf(ActIdentifier act) {
    return the.act().changeDays(Language.ENG).entrySet().stream()
        .filter(each -> each.getValue().contains(act))
        .map(java.util.Map.Entry::getKey)
        .toList();
  }

  /** Returns the history of the provision that {@code place} names in the text of {@code day}. */
  private ProvisionHistory historyOf(LocalDate day, Place place) {
    var text = text(day);
    var eId =
        Location.of(place)
            .in(Location.occurrences(text.eIds()))
            .orElseThrow(() -> new AssertionError("No " + place.words() + " in the text of " + day))
            .eIds()
            .getFirst();
    return the.act().historyOf(eId, text, Language.ENG);
  }

  /** Returns the places of {@code changes}, in their order. */
  private static List<String> words(List<Amendment> changes) {
    return changes.stream().map(each -> each.location().place().words()).toList();
  }

  /** Returns the tags of the built text of the version that starts on {@code day}. */
  private TextTags text(LocalDate day) {
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
