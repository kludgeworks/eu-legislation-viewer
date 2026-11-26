// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules of the history of an act: its amending acts and their order ({@link
 * TheHistoryOfAnAct#amendingActs}), the changes of an amending act ({@link
 * TheHistoryOfAnAct#changesOfAnAmendingAct}), the corrigenda ({@link
 * TheHistoryOfAnAct#corrigendaOfAnAct}), the two texts and the occurrences of a change ({@link
 * TheHistoryOfAnAct#twoTextsOfAnAmendingAct}, {@link TheHistoryOfAnAct#occurrencesOfAChange}), the
 * other acts of a version ({@link TheHistoryOfAnAct#otherActsOfItsVersion}), the history of a
 * provision ({@link TheHistoryOfAnAct#historyOfAProvision}), the occurrences that the data names no
 * act for ({@link TheHistoryOfAnAct#aProvisionTheDataNamesNoActFor}), the amending acts between two
 * days ({@link TheHistoryOfAnAct#amendingActsBetweenTwoDays}) and the days of change ({@link
 * TheHistoryOfAnAct#daysOfChange}). They read the act of the scenario and the texts of its versions
 * that are built ({@link TheAct}), in English unless a step names the language.
 */
public class HistorySteps {
  private final TheAct the;
  private ProvisionHistory history = new ProvisionHistory("", List.of(), List.of());
  private @Nullable LocalDate from;
  private @Nullable LocalDate to;

  public HistorySteps(TheAct the) {
    this.the = the;
  }

  @Then("the amending acts of the act in {language} are, newest first:")
  public void amendingActs(Language language, List<String> cited) {
    var acts = cited.stream().map(Cited::act).toList();
    assertThat(the.amendingActs(language)).containsExactlyElementsOf(acts);
  }

  @Then("{act} is an amending act of the act in {language}")
  public void amending(ActIdentifier act, Language language) {
    assertThat(the.amendingActs(language)).contains(act);
  }

  @Then("{act} is not an amending act of the act in {language}")
  public void notAmending(ActIdentifier act, Language language) {
    assertThat(the.amendingActs(language)).doesNotContain(act);
  }

  @Then("no version of the act includes {act}")
  public void inNoVersion(ActIdentifier act) {
    assertThat(the.act().timeline().versions())
        .noneMatch(version -> version.integrates().contains(act));
  }

  @Then("the first day of {act} is {day}")
  public void firstDay(ActIdentifier act, LocalDate day) {
    assertThat(TheHistoryOfAnAct.firstDay(the.act(), act, Language.ENG)).contains(day);
  }

  @Then("{act} is newer than {act} in the history of the act in {language}")
  public void newer(ActIdentifier newer, ActIdentifier older, Language language) {
    var acts = the.amendingActs(language);
    assertThat(acts).contains(newer, older);
    assertThat(acts.indexOf(newer)).isLessThan(acts.indexOf(older));
  }

  @Then("the latest text that the viewer builds is the text of {day}")
  public void latest(LocalDate day) {
    assertThat(the.latest()).contains(text(day));
  }

  @Then("{act} has {int} changes, and the first three, in the order of the text, are at:")
  public void firstInTextOrder(ActIdentifier act, int count, List<String> places) {
    assertThat(the.latest()).isPresent();
    var changes = the.changesInTextOrder(act);
    assertThat(changes).hasSize(count);
    assertThat(words(changes).subList(0, places.size())).containsExactlyElementsOf(places);
  }

  @Then("{act} has {int} changes, and the first three, in the order of the data, are at:")
  public void firstInDataOrder(ActIdentifier act, int count, List<String> places) {
    assertThat(the.latest()).isEmpty();
    var changes = the.changesInTextOrder(act);
    assertThat(changes).hasSize(count);
    assertThat(words(changes).subList(0, places.size())).containsExactlyElementsOf(places);
  }

  @Then("{act} has no changes in {language}")
  public void noChanges(ActIdentifier act, Language language) {
    assertThat(the.changes(act, language)).isEmpty();
  }

  @Then("{act} has changes in {language}")
  public void changes(ActIdentifier act, Language language) {
    assertThat(the.changes(act, language)).isNotEmpty();
  }

  @Then("in the order of the text, these changes of {act} follow one another:")
  public void followOneAnother(ActIdentifier act, List<String> places) {
    assertThat(the.latest()).isPresent();
    var words = words(the.changesInTextOrder(act));
    assertThat(Collections.indexOfSubList(words, places))
        .as("%s in the order of the text: %s", places, words)
        .isNotNegative();
  }

  @Then("the text of {day} does not have {place}, {place} or {place}, and they are in {place}")
  public void heldBy(LocalDate day, Place first, Place second, Place third, Place holder) {
    var occurrences = TextOccurrences.of(text(day).eIds());
    var held = PlaceWords.location(holder).in(occurrences).orElseThrow().eIds();
    for (var place : List.of(first, second, third)) {
      var location = PlaceWords.location(place);
      assertThat(location.in(occurrences)).as(place.words()).isEmpty();
      assertThat(location.around(occurrences).orElseThrow().eIds())
          .as(place.words())
          .isEqualTo(held);
    }
  }

  @Then("the corrigenda of {act} in {language} are {acts}")
  public void corrigenda(ActIdentifier act, Language language, List<ActIdentifier> corrigenda) {
    assertThat(TheHistoryOfAnAct.corrigendaOfAnAct(the.act(), act, language))
        .containsExactlyElementsOf(corrigenda);
  }

  @Then("the corrigenda of {act} in {language} are none")
  public void noCorrigenda(ActIdentifier act, Language language) {
    assertThat(TheHistoryOfAnAct.corrigendaOfAnAct(the.act(), act, language)).isEmpty();
  }

  @Then("the changes of {act} show between the texts of {day} and of {day}")
  public void twoTexts(ActIdentifier act, LocalDate earlier, LocalDate later) {
    assertThat(
            TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)
                .before()
                .map(Version::date))
        .contains(earlier);
    assertThat(
            TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)
                .integrating()
                .map(Version::date))
        .contains(later);
  }

  @Then("no texts show the changes of {act}")
  public void noTexts(ActIdentifier act) {
    assertThat(
            TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG).integrating())
        .isEmpty();
    assertThat(TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG).before())
        .isEmpty();
  }

  @Then("the occurrences of the change of {act} at {place} are {place} of the later text")
  public void occurrences(ActIdentifier act, Place place, Place occurrence) {
    var later =
        text(TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)
                .integrating()
                .orElseThrow()
                .date())
            .eIds();
    var earlier =
        text(TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)
                .before()
                .orElseThrow()
                .date())
            .eIds();
    var changes =
        the.changes(act, Language.ENG).stream()
            .filter(each -> each.place().words().equals(place.words()))
            .toList();
    assertThat(changes).as("%s at %s", act.cited(), place.words()).hasSize(1);
    var found = TheHistoryOfAnAct.occurrencesOfAChange(changes.getFirst(), later, earlier);
    var expected =
        PlaceWords.location(occurrence)
            .in(TextOccurrences.of(later))
            .orElseThrow(() ->
                new AssertionError("No " + occurrence.words() + " in the later text: " + found))
            .eIds();
    assertThat(found).isEqualTo(expected);
  }

  @Then("the other acts of the first version with {act} are {acts}")
  public void others(ActIdentifier act, List<ActIdentifier> others) {
    assertThat(
            TheHistoryOfAnAct.otherActsOfItsVersion(
                act, TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)))
        .containsExactlyElementsOf(others);
  }

  @Then("the other acts of the first version with {act} are none")
  public void noOthers(ActIdentifier act) {
    assertThat(
            TheHistoryOfAnAct.otherActsOfItsVersion(
                act, TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)))
        .isEmpty();
  }

  @Then("the first version with {act} is the version of {day}")
  public void firstVersion(ActIdentifier act, LocalDate day) {
    assertThat(
            TheHistoryOfAnAct.twoTextsOfAnAmendingAct(the.act(), act, Language.ENG)
                .integrating()
                .map(Version::date))
        .contains(day);
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
        .anyMatch(each -> each.act().equals(act) && each.place().words().equals(place.words()));
  }

  @Then("the text tags it with {act}, which Cellar names no change of there")
  public void tagging(ActIdentifier act) {
    assertThat(history.tagging()).contains(act);
    assertThat(history.changes()).noneMatch(each -> each.act().equals(act));
  }

  @Then("in the text of {day}, the data names no act for {place} and {place}")
  public void unattributed(LocalDate day, Place first, Place second) {
    var occurrences = TextOccurrences.of(text(day).eIds());
    var expected =
        List.of(first, second).stream()
            .map(
                place -> PlaceWords.location(place).in(occurrences).orElseThrow().eIds().getFirst())
            .toList();
    assertThat(
            TheHistoryOfAnAct.aProvisionTheDataNamesNoActFor(
                the.act(), Language.ENG, version(day), the.texts()))
        .containsExactlyElementsOf(expected);
  }

  @Then("in the text of {day}, the data names an act for each occurrence")
  public void noneUnattributed(LocalDate day) {
    assertThat(
            TheHistoryOfAnAct.aProvisionTheDataNamesNoActFor(
                the.act(), Language.ENG, version(day), the.texts()))
        .isEmpty();
  }

  @Then("the first text with {place} is the text of {day}")
  public void firstWith(Place place, LocalDate day) {
    var eId =
        PlaceWords.location(place)
            .in(TextOccurrences.of(text(day).eIds()))
            .orElseThrow()
            .eIds()
            .getFirst();
    assertThat(
            TheHistoryOfAnAct.firstTextWithTheProvision(the.act(), Language.ENG, eId, the.texts())
                .map(Version::date))
        .contains(day);
  }

  @Then("the amending acts between {day} and {day} are {acts}")
  public void between(LocalDate from, LocalDate to, List<ActIdentifier> acts) {
    assertThat(the.between(from, to)).contains(acts);
  }

  @Then("the act has no amending acts between {day} and {day}")
  public void noneBetween(LocalDate from, LocalDate to) {
    assertThat(the.between(from, to)).isEmpty();
  }

  @Then(
      "of the amending acts between {day} and {day}, those that changed {place} in the text of"
          + " {day} are {acts}")
  public void betweenForAProvision(
      LocalDate from, LocalDate to, Place place, LocalDate day, List<ActIdentifier> acts) {
    assertThat(the.between(from, to)).isPresent();
    assertThat(
            TheHistoryOfAnAct.actsOfAProvisionBetweenTwoDays(
                historyOf(day, place), the.between(from, to).orElseGet(List::of)))
        .containsExactlyElementsOf(acts);
  }

  @Then("the act changes on {int} days")
  public void changeDays(int count) {
    assertThat(TheHistoryOfAnAct.daysOfChange(the.overTime(Language.ENG).amendingActs()))
        .hasSize(count);
  }

  @Then("on {day}, these acts change the act: {acts}")
  public void changedOn(LocalDate day, List<ActIdentifier> acts) {
    assertThat(TheHistoryOfAnAct.daysOfChange(the.overTime(Language.ENG).amendingActs()).get(day))
        .containsExactlyElementsOf(acts);
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
    assertThat(the.act().dates().entryIntoForce(act)).contains(day);
  }

  /** Returns the days on which {@code act} changes the act of the scenario, in order. */
  private List<LocalDate> daysOf(ActIdentifier act) {
    return TheHistoryOfAnAct.daysOfChange(the.overTime(Language.ENG).amendingActs())
        .entrySet()
        .stream()
        .filter(each -> each.getValue().contains(act))
        .map(java.util.Map.Entry::getKey)
        .toList();
  }

  /** Returns the history of the provision that {@code place} names in the text of {@code day}. */
  private ProvisionHistory historyOf(LocalDate day, Place place) {
    return TheHistoryOfAnAct.historyOfAProvision(
        the.act(),
        Language.ENG,
        occurrence(day, place),
        text(day),
        TheHistoryOfAnAct.orderOfTheAmendingActs(the.act(), Language.ENG));
  }

  /** Returns the eId of the occurrence that {@code place} names in the text of {@code day}. */
  private String occurrence(LocalDate day, Place place) {
    return PlaceWords.location(place)
        .in(TextOccurrences.of(text(day).eIds()))
        .orElseThrow(() -> new AssertionError("No " + place.words() + " in the text of " + day))
        .eIds()
        .getFirst();
  }

  /** Returns the places of {@code changes}, in their order. */
  private static List<String> words(List<Amendment> changes) {
    return changes.stream().map(each -> each.place().words()).toList();
  }

  @Given("the two texts of {day} and of {day}")
  public void twoTexts(LocalDate earlier, LocalDate later) {
    from = earlier;
    to = later;
  }

  @Then("Cellar's amendments that count are those of {acts}")
  public void countingActs(List<ActIdentifier> acts) {
    assertThat(counting().stream().map(Amendment::act).collect(Collectors.toSet()))
        .isEqualTo(Set.copyOf(acts));
  }

  @Then("{act} is between the two texts, but none of its amendments count")
  public void noneOf(ActIdentifier act) {
    assertThat(the.act().integratedBetween(version(from()), version(to()))).contains(act);
    assertThat(counting()).noneMatch(each -> each.act().equals(act));
  }

  @Then("Cellar's amendment of {act} at {place} does not count: it applies from {day}")
  public void after(ActIdentifier act, Place place, LocalDate day) {
    var at =
        TheHistoryOfAnAct.changesOfAnAmendingAct(the.act(), act, Language.ENG, null).stream()
            .filter(each -> each.place().words().equals(place.words()))
            .toList();
    assertThat(at).as("%s at %s", act.cited(), place.words()).isNotEmpty();
    assertThat(at).allSatisfy(each -> assertThat(each.from()).isEqualTo(day));
    assertThat(counting()).doesNotContainAnyElementsOf(at);
  }

  @Then("Cellar's amendment of {act} at {place} counts")
  public void counts(ActIdentifier act, Place place) {
    assertThat(counting())
        .anyMatch(each -> each.act().equals(act) && each.place().words().equals(place.words()));
  }

  @Then("the viewer finds Cellar's repeals of {act} at {place} and {place} on the earlier text")
  public void onTheEarlierText(ActIdentifier act, Place first, Place second) {
    var earlier = text(from()).eIds();
    for (var place : List.of(first, second)) {
      var found = placed(act, place);
      assertThat(found).as("where %s at %s is found", act.cited(), place.words()).isNotEmpty();
      assertThat(earlier).as(place.words()).containsAll(found);
    }
  }

  @Then("the viewer finds Cellar's repeal of {act} at {place} in {place} of the later text")
  public void atTheLaterText(ActIdentifier act, Place place, Place occurrence) {
    var expected =
        PlaceWords.location(occurrence)
            .in(text(to()).eIds())
            .orElseThrow(() -> new AssertionError("The later text has no " + occurrence.words()))
            .eIds();
    assertThat(placed(act, place)).containsExactlyElementsOf(expected);
  }

  /** Returns the occurrences at which the removals of {@code act} at {@code place} are found. */
  private List<String> placed(ActIdentifier act, Place place) {
    return amended().byOccurrence().entrySet().stream()
        .filter(each ->
            each.getValue().stream()
                .anyMatch(amendment ->
                    amendment.act().equals(act)
                        && amendment.role().removes()
                        && amendment.place().words().equals(place.words())))
        .map(Map.Entry::getKey)
        .toList();
  }

  /** Returns the amendments that count between the two texts, each one time. */
  private List<Amendment> counting() {
    return amended().byOccurrence().values().stream().flatMap(List::stream).distinct().toList();
  }

  /** Returns Cellar's amendments that count between the two texts, at their occurrences. */
  private Amended amended() {
    return the.act()
        .amended(
            version(from()), version(to()), text(from()).eIds(), text(to()).eIds(), Language.ENG);
  }

  private LocalDate from() {
    return Objects.requireNonNull(from, "No texts: the scenario gives \"the two texts of …\"");
  }

  private LocalDate to() {
    return Objects.requireNonNull(to, "No texts: the scenario gives \"the two texts of …\"");
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
