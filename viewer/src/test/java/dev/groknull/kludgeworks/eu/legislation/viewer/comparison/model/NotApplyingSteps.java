// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The steps of the rule about a change that does not apply yet on the day of the page. The
 * differences are those that the Differ finds between the converter's sample texts of the
 * comparison ({@link Comparing}). The tags of the two texts and the dates of the acts tell which
 * change does not apply yet ({@code Act.prospective}). The steps reach the domain only: {@link
 * Attributed}, {@link Differ}, the content's outlines and the catalogue's acts.
 */
public class NotApplyingSteps {
  private final Comparing comparing;
  private List<Attributed> differences = List.of();

  public NotApplyingSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @When("the reader reads the comparison on {day}")
  public void on(LocalDate day) {
    var act = comparing.act();
    var texts = new HashMap<Version, TextTags>();
    for (var version : act.timeline().versions()) {
      if (Comparing.hasText(version)) {
        texts.put(version, TextTags.of(Outlines.of(Comparing.text(version))));
      }
    }
    var prospective = LawOnADay.of(act).prospective(comparing.to(), day, Language.ENG, texts);
    differences =
        Differ.differences(comparing.earlier(), comparing.later()).differences().stream()
            .map(
                difference -> new Attributed(difference, Attribution.NONE).notApplying(prospective))
            .toList();
  }

  @Then("at the {word} {place}, the difference says that {act} does not apply yet on {day}")
  public void notApplying(String kind, Place place, ActIdentifier amending, LocalDate day) {
    var notApplying = at(kind, place).notApplying();
    assertThat(notApplying).as(place.words()).isNotNull();
    assertThat(notApplying).isEqualTo(new Attributed.NotApplying(amending, day));
  }

  @Then("at the {word} {place}, the difference says that each change applies")
  public void applying(String kind, Place place) {
    assertThat(at(kind, place).notApplying()).as(place.words()).isNull();
  }

  @Then("{int} of the {int} differences say that {act} does not apply yet")
  public void count(int notApplying, int all, ActIdentifier amending) {
    assertThat(differences).hasSize(all);
    assertThat(
            differences.stream()
                .filter(each -> each.notApplying() != null)
                .map(each -> Objects.requireNonNull(each.notApplying()).act()))
        .hasSize(notApplying)
        .containsOnly(amending);
  }

  private Attributed at(String kind, Place place) {
    var expected = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    var text = Occurrences.of(expected == Kind.DELETED ? comparing.earlier() : comparing.later());
    var eId = Comparing.eId(place, text);
    var found =
        differences.stream()
            .filter(each -> each.difference().eId().equals(eId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No difference at " + place.words()));
    assertThat(found.difference().kind()).as(place.words()).isEqualTo(expected);
    return found;
  }
}
