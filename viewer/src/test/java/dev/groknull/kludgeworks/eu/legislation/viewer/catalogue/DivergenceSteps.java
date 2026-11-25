// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules of the text and the law on a day: the version that a reader gets on a day
 * ({@link LawOnADay#version}), the divergences of the versions in English ({@link
 * LawOnADay#divergences}), the deferred application of the act ({@link
 * LawOnADay#deferredApplication}) and the acts that made a text in a language ({@link
 * LawOnADay#madeBy}). They read the act of the scenario and the texts of its versions that are
 * built ({@link TheAct}).
 */
public class DivergenceSteps {
  private final TheAct the;
  private @Nullable Divergence divergence;

  public DivergenceSteps(TheAct the) {
    this.the = the;
  }

  @Then("on {day}, the reader gets the version of {day}")
  public void version(LocalDate day, LocalDate version) {
    assertThat(the.act().timeline().applicableOn(day, Language.ENG).map(Version::date))
        .as("the version on %s", day)
        .contains(version);
  }

  @Then("on {day}, the reader gets no version")
  public void noVersion(LocalDate day) {
    assertThat(the.act().timeline().applicableOn(day, Language.ENG))
        .as("the version on %s", day)
        .isEmpty();
  }

  @Then("the text of {day} does not have the place of the change of {act} at {place}")
  public void textLacks(LocalDate day, ActIdentifier act, Place place) {
    var text = Objects.requireNonNull(the.texts().get(version(day)), "No built text of " + day);
    var changes =
        TheHistoryOfAnAct.changesOfAnAmendingAct(the.act(), act, Language.ENG, null).stream()
            .filter(each -> each.place().words().equals(place.words()))
            .toList();
    assertThat(changes).as("%s at %s", act.cited(), place.words()).hasSize(1);
    assertThat(changes.getFirst().location().in(TextOccurrences.of(text.eIds())))
        .as("%s in the text of %s", place.words(), day)
        .isEmpty();
  }

  @Then("{act} is a pending amendment from {day}, until the version of {day} includes it")
  public void pending(ActIdentifier act, LocalDate from, LocalDate included) {
    divergence = the(act);
    assertThat(divergence.kind()).isEqualTo(Divergence.Kind.PENDING);
    assertThat(divergence.appliesFrom()).isEqualTo(from);
    assertThat(divergence.included()).isNotNull().extracting(Version::date).isEqualTo(included);
  }

  @Then("{act} is a prospective amendment from the version of {day}, until it applies on {day}")
  public void prospective(ActIdentifier act, LocalDate included, LocalDate applies) {
    divergence = the(act);
    assertThat(divergence.kind()).isEqualTo(Divergence.Kind.PROSPECTIVE);
    assertThat(divergence.included()).isNotNull().extracting(Version::date).isEqualTo(included);
    assertThat(divergence.appliesFrom()).isEqualTo(applies);
  }

  @Then("{act} is an undated pending amendment from {day}")
  public void undated(ActIdentifier act, LocalDate from) {
    divergence = the(act);
    assertThat(divergence.kind()).isEqualTo(Divergence.Kind.UNDATED);
    assertThat(divergence.appliesFrom()).isEqualTo(from);
  }

  @Then("{act} makes no divergence")
  public void none(ActIdentifier act) {
    assertThat(divergencesOf(act)).as(act.cited()).isEmpty();
  }

  @Then("its divergence is at {place}")
  public void at(Place place) {
    assertThat(places()).as("the places of the divergence").contains(place.words());
  }

  @Then("its divergence is not at {place}")
  public void notAt(Place place) {
    assertThat(places()).as("the places of the divergence").doesNotContain(place.words());
  }

  @Then("its divergence is at {int} places")
  public void count(int places) {
    assertThat(places()).as("the places of the divergence").hasSize(places);
  }

  @Then("its divergence is at no place")
  public void nowhere() {
    assertThat(places()).as("the places of the divergence").isEmpty();
  }

  @Then("on {day}, {act} makes the text and the law differ")
  public void differ(LocalDate day, ActIdentifier act) {
    assertThat(on(day, act)).as("%s on %s", act.cited(), day).isTrue();
  }

  @Then("on {day}, {act} does not make the text and the law differ")
  public void agree(LocalDate day, ActIdentifier act) {
    assertThat(on(day, act)).as("%s on %s", act.cited(), day).isFalse();
  }

  @Then("on {day}, the act has no deferred application")
  public void notDeferred(LocalDate day) {
    assertThat(
            Optional.ofNullable(
                LawOverTime.of(the.act(), Language.ENG, Map.of()).on(day).deferredApplication()))
        .as(day.toString())
        .isEmpty();
  }

  @Then("on {day}, the act has a deferred application: it applies in full from {day}")
  public void deferred(LocalDate day, LocalDate until) {
    assertThat(
            Optional.ofNullable(
                LawOverTime.of(the.act(), Language.ENG, Map.of()).on(day).deferredApplication()))
        .as(day.toString())
        .hasValueSatisfying(deferred -> {
          assertThat(deferred.inFull().date()).isEqualTo(until);
          assertThat(deferred.partial()).isFalse();
        });
  }

  @Then("in {language}, no act made the text of {day}")
  public void madeByNone(Language language, LocalDate day) {
    assertThat(
            TheTextAndTheLawOnADay.theActsThatMadeATextInTheLanguage(
                the.act(), version(day), language))
        .isEmpty();
  }

  @Then("in {language}, these acts made the text of {day}: {acts}")
  public void madeBy(Language language, LocalDate day, List<ActIdentifier> acts) {
    assertThat(
            TheTextAndTheLawOnADay.theActsThatMadeATextInTheLanguage(
                the.act(), version(day), language))
        .containsExactlyElementsOf(acts);
  }

  @Then("on {day}, the text is not the law for {acts}")
  public void notTheLaw(LocalDate day, List<ActIdentifier> acts) {
    assertThat(notTheLaw(day)).containsExactlyElementsOf(acts);
  }

  @Then("on {day}, the text is the law")
  public void theLaw(LocalDate day) {
    assertThat(notTheLaw(day)).isEmpty();
  }

  /**
   * Returns the acts for which the text of {@code day} is not the law: the act itself while its
   * application is deferred, then the acts of the divergences of the day.
   */
  private List<ActIdentifier> notTheLaw(LocalDate day) {
    return LawOverTime.of(the.act(), Language.ENG, the.texts())
        .on(day)
        .notTheLaw()
        .acts(the.act().id());
  }

  private boolean on(LocalDate day, ActIdentifier act) {
    return LawOverTime.of(the.act(), Language.ENG, the.texts()).on(day).divergences().stream()
        .anyMatch(each -> each.act().equals(act));
  }

  private Divergence the(ActIdentifier act) {
    var found = divergencesOf(act);
    assertThat(found).as("the divergences of %s", act.cited()).hasSize(1);
    return found.getFirst();
  }

  private List<Divergence> divergencesOf(ActIdentifier act) {
    return LawOverTime.of(the.act(), Language.ENG, the.texts()).divergences().stream()
        .filter(each -> each.act().equals(act))
        .toList();
  }

  private List<String> places() {
    var found = Objects.requireNonNull(divergence, "No divergence: name the divergence first");
    return found.places().stream().map(each -> each.place().words()).toList();
  }

  /** Returns the one version of the act that starts on {@code day}. */
  private Version version(LocalDate day) {
    var found =
        the.act().timeline().versions().stream().filter(each -> each.date().equals(day)).toList();
    assertThat(found).as("the versions of %s", day).hasSize(1);
    return found.getFirst();
  }
}
