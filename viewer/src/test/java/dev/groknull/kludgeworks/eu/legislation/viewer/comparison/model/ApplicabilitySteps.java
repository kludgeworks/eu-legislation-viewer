// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The steps of the rules about from when a change applies, on 2015/1998 as Cellar's recorded
 * answers make it ({@link Comparing}). A step can give an amending act dates of its own, for a case
 * that the recorded acts do not have. The steps reach the domain only: {@link Applicability} and
 * the catalogue's acts.
 */
public class ApplicabilitySteps {
  private final Comparing comparing;
  private final List<Amendment> amendments = new ArrayList<>();
  private Optional<Applicability> applicability = Optional.empty();

  public ApplicabilitySteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @Given("Cellar dates the change of {act} at the occurrence from {day}")
  public void cellarDay(ActIdentifier amending, LocalDate day) {
    amendments.add(
        new Amendment(
            amending,
            Location.parse("AN PO 1"),
            new Place("annex point 1"),
            Amendment.Role.REPLACEMENT,
            day,
            null,
            Set.of()));
  }

  @When("the reader asks from when the change of {act} applies")
  public void when(ActIdentifier amending) {
    applicability =
        Applicability.of(
            act(),
            Inclusions.listed(act().timeline()),
            comparing.from(),
            comparing.to(),
            amending,
            amendments,
            false);
  }

  @Then("it applies from {day}")
  public void appliesFrom(LocalDate day) {
    assertThat(applying())
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(day);
              assertThat(from.entryIntoForce()).as("only its entry into force").isFalse();
            });
  }

  @Then("it applies from {day}, its entry into force")
  public void appliesFromItsEntryIntoForce(LocalDate day) {
    assertThat(applying())
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(day);
              assertThat(from.entryIntoForce()).as("only its entry into force").isTrue();
            });
  }

  @Then("it applies from more than one day")
  public void appliesFromMoreThanOneDay(List<Applying.Start> starts) {
    assertThat(applying())
        .isInstanceOfSatisfying(
            Applying.FromMoreThanOneDay.class,
            parts -> assertThat(parts.starts()).containsExactlyElementsOf(starts));
  }

  @Then("it applies from a day that {word} {int} of {act} sets")
  public void appliesFromADaySetBy(String type, int number, ActIdentifier amending) {
    assertThat(applying()).isInstanceOf(Applying.SetBy.class);
    assertThat(applies().clause())
        .extracting(ActDate.Provision::cited)
        .containsExactly(type + " " + number);
    assertThat(applies().act()).isEqualTo(amending);
  }

  @Then("it is in force from {day}, and the data does not say from when it applies")
  public void inForce(LocalDate day) {
    assertThat(applying())
        .isInstanceOfSatisfying(
            Applying.InForce.class, inForce -> assertThat(inForce.day()).isEqualTo(day));
  }

  @Then("the sidenote says nothing of when it applies")
  public void nothing() {
    assertThat(applicability).isEmpty();
  }

  @Then("{act} moved the date")
  public void moved(ActIdentifier mover) {
    assertThat(applies().amendedBy()).containsExactly(mover);
  }

  @Then("no act moved the date")
  public void notMoved() {
    assertThat(applies().amendedBy()).isEmpty();
  }

  @Then("it corrects {act} from {day}")
  public void corrects(ActIdentifier corrected, LocalDate day) {
    var corrects = correction();
    assertThat(corrects.corrected()).isEqualTo(corrected);
    assertThat(corrects.from()).isEqualTo(day);
  }

  @Then("the first text between the two days that has it is of {day}")
  public void firstIn(LocalDate day) {
    assertThat(correction().firstIn()).isEqualTo(day);
  }

  private Applicability.Applies applies() {
    assertThat(applicability).isPresent();
    assertThat(applicability.orElseThrow()).isInstanceOf(Applicability.Applies.class);
    return (Applicability.Applies) applicability.orElseThrow();
  }

  private Applying applying() {
    return applies().applying();
  }

  private Applicability.Corrects correction() {
    assertThat(applicability).isPresent();
    assertThat(applicability.orElseThrow()).isInstanceOf(Applicability.Corrects.class);
    return (Applicability.Corrects) applicability.orElseThrow();
  }

  private Act act() {
    return comparing.act();
  }
}
