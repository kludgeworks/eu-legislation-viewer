// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.LastChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rule about what last made a provision that is the same in both texts as it is,
 * as the Synopsis shows it beside a neighbour. The neighbour is an occurrence of the converter's
 * sample text of the later day ({@link Comparing}), with its tags ({@link Neighbours}). The steps
 * reach the domain only: {@link LastChange} and the catalogue's acts.
 */
public class LastChangeSteps {
  private final Comparing comparing;
  private @Nullable LastChange last;

  public LastChangeSteps(Comparing comparing) {
    this.comparing = comparing;
  }

  @When("the reader asks what last changed the neighbour {place}")
  public void lastChanged(Place place) {
    var text = Comparing.text(comparing.to());
    var eId = Comparing.eId(place, Occurrences.of(text));
    var neighbour =
        Objects.requireNonNull(Neighbours.of(text, Set.of(eId)).get(eId), place.words());
    last =
        LastChange.of(
            comparing.act(),
            Inclusions.listed(comparing.act().timeline()),
            comparing.to(),
            neighbour.tags());
  }

  @Then("{act} last changed it: {modification}, dated from {day}")
  public void lastChangedBy(ActIdentifier act, Modification modification, LocalDate day) {
    assertThat(last)
        .isInstanceOfSatisfying(
            LastChange.ByAct.class,
            by -> {
              assertThat(by.act()).isEqualTo(act);
              assertThat(by.modification()).isEqualTo(modification);
              assertThat(day(by.applicability())).isEqualTo(day);
            });
  }

  @Then("it is as in the original act, dated from {day}")
  public void original(LocalDate day) {
    assertThat(last)
        .isInstanceOfSatisfying(
            LastChange.Original.class,
            original -> assertThat(day(original.applicability())).isEqualTo(day));
  }

  /** Returns the first day of {@code applicability}: the day of its first date. */
  private static @Nullable LocalDate day(@Nullable Applicability applicability) {
    if (!(applicability instanceof Applicability.Applies applies)) {
      return null;
    }
    return switch (applies.applying()) {
      case Applying.From from -> from.day();
      case Applying.FromMoreThanOneDay parts -> parts.starts().getFirst().day();
      case Applying.InForce inForce -> inForce.day();
      case Applying.SetBy _ -> null;
    };
  }
}
