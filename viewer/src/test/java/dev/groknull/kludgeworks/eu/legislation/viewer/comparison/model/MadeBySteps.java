// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Appearance;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about which acts made a difference, and in which steps. The difference is
 * the one that the Differ finds between the converter's sample texts of the comparison ({@link
 * Comparing}), with Cellar's recorded amendments of its occurrence. The steps reach the domain
 * only: {@link Attribution}, {@link Differ} and the catalogue's acts.
 */
public class MadeBySteps {
  private final Comparing comparing;

  private @Nullable Difference difference;
  private @Nullable Set<LocalDate> built;
  private @Nullable Version appeared;
  private List<Amendment> amendments = List.of();
  private List<Amendment> removals = List.of();
  private Attribution attribution = Attribution.NONE;

  public MadeBySteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @Given("the difference at the {word} {place}")
  public void difference(String kind, Place place) {
    var earlier = Occurrences.of(comparing.earlier());
    var later = Occurrences.of(comparing.later());
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var eId = Comparing.eId(place, expected == Kind.DELETED ? earlier : later);
    var found =
        Differ.differences(comparing.earlier(), comparing.later()).differences().stream()
            .filter(each -> each.eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(found.kind()).as(place.words()).isEqualTo(expected);
    difference = found;
    var amended =
        comparing
            .act()
            .amended(
                comparing.from(),
                comparing.to(),
                Outlines.of(comparing.earlier()).eIds(),
                Outlines.of(comparing.later()).eIds(),
                Language.ENG);
    amendments = Attribution.amendmentsOf(amended, found);
    removals = Attribution.removalsOf(amended, found);
  }

  @When("the reader asks who made it")
  public void whoMadeIt() {
    attribution =
        Attribution.of(
            comparing.act(),
            Inclusions.listed(comparing.act().timeline()),
            comparing.from(),
            comparing.to(),
            Objects.requireNonNull(difference, "no difference given"),
            amendments,
            removals);
  }

  @Then("the tags of the difference are")
  public void theTagsOfTheDifferenceAre(List<Types.TagRow> rows) {
    var later = Occurrences.of(comparing.later());
    assertThat(Objects.requireNonNull(difference, "no difference given").tags())
        .containsExactlyElementsOf(
            rows.stream()
                .map(
                    row ->
                        new Difference.Tag(
                            row.act().celex(),
                            row.modification(),
                            row.takenFrom() == null
                                ? null
                                : Comparing.eId(Objects.requireNonNull(row.takenFrom()), later)))
                .toList());
  }

  @Then("the difference has no tags")
  public void theDifferenceHasNoTags() {
    assertThat(Objects.requireNonNull(difference, "no difference given").tags()).isEmpty();
  }

  @Then("the steps are")
  public void theStepsAre(List<Types.StepRow> rows) {
    assertThat(
            attribution.steps().stream()
                .map(
                    step ->
                        new Types.StepRow(step.version().date(), step.modification(), step.acts()))
                .toList())
        .containsExactlyElementsOf(rows);
  }

  @Given("only the texts of {days} are built")
  public void onlyBuilt(List<LocalDate> days) {
    built = Set.copyOf(days);
  }

  @When("the reader asks where the change first appears")
  public void whereItAppears() {
    var eId = Objects.requireNonNull(difference, "no difference given").eId();
    appeared =
        Appearance.of(
                comparing.act().timeline(),
                comparing.from(),
                comparing.to(),
                Set.of(eId),
                this::differing)
            .get(eId);
  }

  @Then("no act is named")
  public void noActIsNamed() {
    assertThat(attribution.steps()).isEmpty();
    assertThat(
            Attribution.unnamed(
                Inclusions.listed(comparing.act().timeline()), comparing.from(), comparing.to()))
        .isEqualTo(Attribution.NONE);
  }

  @Then("it first appears in the text of {day}, which consolidates {acts}")
  public void firstAppearsIn(LocalDate day, List<ActIdentifier> acts) {
    var version = Objects.requireNonNull(appeared, "the change appears in no text");
    assertThat(version.date()).isEqualTo(day);
    assertThat(
            Inclusions.listed(comparing.act().timeline()).of(version).stream()
                .map(ActIdentifier::cited)
                .toList())
        .isEqualTo(acts.stream().map(ActIdentifier::cited).toList());
  }

  /**
   * Returns the eIds of the differences between the text of the version before {@code version} and
   * its own text, or empty if the two texts are not both built. A text is built if the converter
   * has its sample text, and if the scenario does not limit the built texts to others.
   */
  private Optional<Set<String>> differing(Version version) {
    return comparing
        .act()
        .timeline()
        .predecessor(version)
        .filter(this::isBuilt)
        .filter(before -> isBuilt(version))
        .map(
            before ->
                Differ.differences(Comparing.text(before), Comparing.text(version))
                    .differences()
                    .stream()
                    .map(Difference::eId)
                    .collect(Collectors.toSet()));
  }

  private boolean isBuilt(Version version) {
    return (built == null || built.contains(version.date())) && Comparing.hasText(version);
  }

  @Then("{act} is not named")
  public void notNamed(ActIdentifier act) {
    assertThat(attribution.acts()).doesNotContain(act);
  }

  @Then("{act} is named for its part that applies from {day}")
  public void namedForItsPart(ActIdentifier act, LocalDate day) {
    var step =
        attribution.steps().stream()
            .filter(each -> each.acts().contains(act))
            .findFirst()
            .orElseThrow(() -> new AssertionError(act.cited() + " is not named"));
    assertThat(step.forPart()).contains(act);
    var applicability =
        Applicability.of(
                comparing.act(),
                Inclusions.listed(comparing.act().timeline()),
                comparing.from(),
                comparing.to(),
                act,
                amendments,
                true)
            .orElseThrow();
    assertThat(applicability)
        .isInstanceOfSatisfying(
            Applicability.Applies.class,
            applies ->
                assertThat(applies.applying())
                    .isInstanceOfSatisfying(
                        Applying.From.class, from -> assertThat(from.day()).isEqualTo(day)));
  }
}
