// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Appearance;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about which acts made a difference, and in which steps (R-WHO-01,
 * R-WHO-04, R-WHO-05, R-WHO-06, R-WHO-07, R-WHO-09, R-WHO-11, R-WHO-12, R-WHO-22). The difference
 * is the one that the Differ finds between the converter's sample texts of the comparison ({@link
 * Comparing}), with Cellar's recorded amendments of its occurrence. The steps reach the domain
 * only: {@link Attribution}, {@link Differ} and the catalogue's acts.
 */
public class MadeBySteps {
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

  private @Nullable Difference difference;
  private @Nullable Set<LocalDate> built;
  private @Nullable Version appeared;
  private List<Amendment> amendments = List.of();
  private Attribution attribution = Attribution.NONE;

  @Given("the difference at the {word} {place}")
  public void difference(String kind, Place place) {
    var earlier = Occurrences.of(Comparing.text(Comparing.from()));
    var later = Occurrences.of(Comparing.text(Comparing.to()));
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var eId = eId(place, expected == Kind.DELETED ? earlier : later);
    var found =
        Differ.differences(Comparing.text(Comparing.from()), Comparing.text(Comparing.to()))
            .differences()
            .stream()
            .filter(each -> each.eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(found.kind()).as(place.words()).isEqualTo(expected);
    difference = found;
    amendments =
        Attribution.amendmentsOf(
            Comparing.act()
                .amended(
                    Comparing.from(),
                    Comparing.to(),
                    List.copyOf(earlier.byEId().keySet()),
                    List.copyOf(later.byEId().keySet()),
                    Language.ENG),
            found);
  }

  @When("the reader asks who made it")
  public void whoMadeIt() {
    attribution =
        Attribution.of(
            Comparing.act(),
            Inclusions.listed(Comparing.act().timeline()),
            Comparing.from(),
            Comparing.to(),
            Objects.requireNonNull(difference, "no difference given"),
            amendments);
  }

  @Then("the steps are")
  public void theStepsAre(List<Map<String, String>> rows) {
    assertThat(
            attribution.steps().stream()
                .map(
                    step ->
                        List.of(
                            step.version().date().format(DAY),
                            step.modification().word(),
                            String.join(
                                " and ", step.acts().stream().map(ActIdentifier::cited).toList())))
                .toList())
        .containsExactlyElementsOf(
            rows.stream()
                .map(
                    row ->
                        List.of(
                            Objects.requireNonNull(row.get("text of")),
                            Objects.requireNonNull(row.get("modification")),
                            String.join(
                                " and ",
                                Cited.acts(Objects.requireNonNull(row.get("acts"))).stream()
                                    .map(ActIdentifier::cited)
                                    .toList())))
                .toList());
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
                Comparing.act().timeline(),
                Comparing.from(),
                Comparing.to(),
                Set.of(eId),
                this::differing)
            .get(eId);
  }

  @Then("no act is named")
  public void noActIsNamed() {
    assertThat(attribution.steps()).isEmpty();
    assertThat(
            Attribution.unnamed(
                Inclusions.listed(Comparing.act().timeline()), Comparing.from(), Comparing.to()))
        .isEqualTo(Attribution.NONE);
  }

  @Then("it first appears in the text of {day}, which consolidates {string}")
  public void firstAppearsIn(LocalDate day, String acts) {
    var version = Objects.requireNonNull(appeared, "the change appears in no text");
    assertThat(version.date()).isEqualTo(day);
    assertThat(
            Inclusions.listed(Comparing.act().timeline()).of(version).stream()
                .map(ActIdentifier::cited)
                .toList())
        .isEqualTo(Cited.acts(acts).stream().map(ActIdentifier::cited).toList());
  }

  /**
   * Returns the eIds of the differences between the text of the version before {@code version} and
   * its own text, or empty if the two texts are not both built. A text is built if the converter
   * has its sample text, and if the scenario does not limit the built texts to others.
   */
  private Optional<Set<String>> differing(Version version) {
    return Comparing.act()
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
    assertThat(step.inPart()).contains(act);
    var applicability =
        Applicability.of(
                Comparing.act(),
                Inclusions.listed(Comparing.act().timeline()),
                Comparing.from(),
                Comparing.to(),
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

  /** Returns the eId of the one occurrence of {@code text} that is {@code place}. */
  private static String eId(Place place, Occurrences text) {
    var found =
        Location.of(place)
            .in(List.copyOf(text.byEId().keySet()))
            .orElseThrow(() -> new AssertionError("The text has no " + place.words()));
    assertThat(found.eIds()).as(place.words()).hasSize(1);
    assertThat(found.within()).as(place.words()).isFalse();
    return found.eIds().getFirst();
  }
}
