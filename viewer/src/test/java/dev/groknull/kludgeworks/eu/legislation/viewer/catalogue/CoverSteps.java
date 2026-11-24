// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of what a comparison covers: its two days, its two versions, the version of a day in a
 * language, and the acts between its versions. The timeline is the act's in English.
 */
public class CoverSteps {
  private final TheAct the;
  private Timeline.@Nullable TwoDays days;

  public CoverSteps(TheAct the) {
    this.the = the;
  }

  private Timeline english() {
    return the.act().timeline().in(Language.ENG).orElseThrow();
  }

  @When("the reader gives no days on {day}")
  public void noDays(LocalDate today) {
    days = english().twoDays(null, null, today);
  }

  @When("the reader gives From {day} and To {day}")
  public void both(LocalDate from, LocalDate to) {
    days = english().twoDays(from, to, to);
  }

  @When("the reader gives only To {day}")
  public void onlyTo(LocalDate to) {
    days = english().twoDays(null, to, to);
  }

  @When("the reader gives only From {day}, on {day}")
  public void onlyFrom(LocalDate from, LocalDate today) {
    days = english().twoDays(from, null, today);
  }

  @Then("the comparison is from {day} to {day}")
  public void comparison(LocalDate from, LocalDate to) {
    assertThat(Objects.requireNonNull(days)).isEqualTo(new Timeline.TwoDays(from, to));
  }

  @Then("the viewer compares the law on {day} and on {day} at the versions of {day} and of {day}")
  public void lookedUp(LocalDate from, LocalDate to, LocalDate earlier, LocalDate later) {
    var versions = LawOnADay.of(the.act()).versionsOn(from, to, Language.ENG).orElseThrow();
    assertThat(List.of(versions.from().date(), versions.to().date()))
        .containsExactly(earlier, later);
  }

  @Then("a comparison is of the versions of {day} and of {day}")
  public void canonical(LocalDate from, LocalDate to) {
    var versions = english().versionsStartingOn(from, to).orElseThrow();
    assertThat(List.of(versions.from().date(), versions.to().date())).containsExactly(from, to);
  }

  @Then("no comparison is at {day} and {day}")
  public void notCanonical(LocalDate from, LocalDate to) {
    assertThat(english().versionsStartingOn(from, to)).isEmpty();
  }

  @Then("in {language}, the version of {day} is the original text")
  public void original(Language language, LocalDate day) {
    var version = the.act().timeline().applicableOn(day, language).orElseThrow();
    assertThat(version.kind()).isEqualTo(Version.Kind.ORIGINAL);
    assertThat(version.languages()).contains(language);
  }

  @Then("in {language}, the version of {day} is the consolidation")
  public void consolidation(Language language, LocalDate day) {
    var version = the.act().timeline().applicableOn(day, language).orElseThrow();
    assertThat(version.kind()).isEqualTo(Version.Kind.CONSOLIDATED);
    assertThat(version.languages()).contains(language);
  }

  @Then("Cellar lists {acts} for the version of {day}")
  public void listed(List<ActIdentifier> acts, LocalDate day) {
    assertThat(english().startingOn(day).orElseThrow().integrates())
        .containsExactlyElementsOf(acts);
  }

  @Then("the acts between the versions of {day} and of {day} include {acts}")
  public void between(LocalDate from, LocalDate to, List<ActIdentifier> acts) {
    assertThat(betweenActs(from, to)).containsAll(acts);
  }

  @Then("the acts between the versions of {day} and of {day} do not include {acts}")
  public void notBetween(LocalDate from, LocalDate to, List<ActIdentifier> acts) {
    assertThat(betweenActs(from, to)).doesNotContainAnyElementsOf(acts);
  }

  @Then("a comparison of the versions of {day} and of {day} can show the changes of {act} only")
  public void oneAct(LocalDate from, LocalDate to, ActIdentifier act) {
    assertThat(oneBetween(from, to, act)).isTrue();
  }

  @Then("a comparison of the versions of {day} and of {day} cannot show the changes of {act} only")
  public void notOneAct(LocalDate from, LocalDate to, ActIdentifier act) {
    assertThat(oneBetween(from, to, act)).isFalse();
  }

  private boolean oneBetween(LocalDate from, LocalDate to, ActIdentifier act) {
    var timeline = english();
    return the.act()
        .inclusions(Language.ENG, the.texts())
        .oneBetween(
            timeline.startingOn(from).orElseThrow(),
            timeline.startingOn(to).orElseThrow(),
            act.celex())
        .isPresent();
  }

  private List<ActIdentifier> betweenActs(LocalDate from, LocalDate to) {
    var timeline = english();
    return List.copyOf(
        the.act()
            .inclusions(Language.ENG, the.texts())
            .between(
                timeline.startingOn(from).orElseThrow(), timeline.startingOn(to).orElseThrow()));
  }
}
