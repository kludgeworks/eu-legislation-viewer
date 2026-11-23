// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Alternatives;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Inclusion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about the changes of one act, and about the differences that one of
 * several acts made. The differences are those that the Differ finds between the converter's sample
 * texts of the comparison ({@link Comparing}), each with who made it ({@link Attribution#of}). The
 * steps reach the domain only.
 */
public class OneActSteps {
  private final Comparing comparing;

  private @Nullable Inclusion inclusion;
  private List<Attributed> unnamed = List.of();
  private @Nullable Alternatives alternatives;
  private List<Difference> changes = List.of();

  public OneActSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @When("the comparison is limited to {act}")
  public void limitedTo(ActIdentifier act) {
    inclusion = inclusion(act);
  }

  @Then("its changes are those from the text of {day} to the text of {day}")
  public void itsChanges(LocalDate before, LocalDate first) {
    var found = Objects.requireNonNull(inclusion, "no act given");
    assertThat(List.of(found.before().date(), found.first().date())).containsExactly(before, first);
  }

  @When("the reader asks who made the differences that no act is named for")
  public void unnamed() {
    unnamed =
        attributed(comparing.from(), comparing.to()).stream()
            .filter(each -> each.attribution().acts().isEmpty())
            .toList();
  }

  @Then("{int} differences are made by one of {acts}, and the data does not say which")
  public void madeByOneOf(int count, List<ActIdentifier> acts) {
    assertThat(unnamed).hasSize(count);
    assertThat(unnamed)
        .allSatisfy(
            each -> assertThat(each.attribution().candidates()).containsExactlyElementsOf(acts));
  }

  @When("the reader opens the page of {act}")
  public void pageOf(ActIdentifier act) {
    var found = inclusion(act);
    alternatives = Alternatives.of(act, attributed(found.before(), found.first()));
  }

  @Then("{int} differences are listed apart, by this act or by {acts}")
  public void listedApart(int count, List<ActIdentifier> others) {
    var found = Objects.requireNonNull(alternatives, "no page opened");
    assertThat(found.differences()).hasSize(count);
    assertThat(found.others()).containsExactlyElementsOf(others);
  }

  @When("the reader asks for the changes of {act} at {place}")
  public void changesAt(ActIdentifier act, Place place) {
    var found = inclusion(act);
    var attributed = attributed(found.before(), found.first());
    var later = Occurrences.of(Comparing.text(found.first()));
    var at =
        Location.of(place)
            .in(List.copyOf(later.byEId().keySet()))
            .orElseThrow(() -> new AssertionError("The text has no " + place.words()));
    assertThat(at.eIds()).as(place.words()).hasSize(1);
    var differences = attributed.stream().map(Attributed::difference).toList();
    var comparison = new Comparison(new TextId(1), new TextId(2), differences, List.of(), null);
    assertThat(comparison.at(at.eIds()))
        .as("%s has no difference of its own", place.words())
        .isEmpty();
    changes = Attribution.madeIn(attributed, at.eIds(), act);
  }

  @Then("its changes are {int} difference(s) in Attachment {word}")
  public void changesIn(int count, String attachment) {
    assertThat(changes).hasSize(count);
    assertThat(changes)
        .allSatisfy(
            each -> assertThat(each.eId()).contains("level_ATTACHMENT-" + attachment + "/"));
  }

  /** Returns the texts of the changes of {@code act} in the comparison. */
  private Inclusion inclusion(ActIdentifier act) {
    return Inclusion.of(
            comparing.act().timeline(),
            Inclusions.listed(comparing.act().timeline()),
            comparing.from(),
            comparing.to(),
            act)
        .orElseThrow(() -> new AssertionError("No text includes " + act.cited()));
  }

  /** Returns each difference between the texts of {@code from} and {@code to}, with who made it. */
  private List<Attributed> attributed(Version from, Version to) {
    var earlier = Comparing.text(from);
    var later = Comparing.text(to);
    var amended =
        comparing
            .act()
            .amended(
                from,
                to,
                List.copyOf(Occurrences.of(earlier).byEId().keySet()),
                List.copyOf(Occurrences.of(later).byEId().keySet()),
                Language.ENG);
    var included = Inclusions.listed(comparing.act().timeline());
    return Differ.differences(earlier, later).differences().stream()
        .map(
            difference ->
                new Attributed(
                    difference,
                    Attribution.of(
                        comparing.act(),
                        included,
                        from,
                        to,
                        difference,
                        Attribution.amendmentsOf(amended, difference))))
        .toList();
  }
}
