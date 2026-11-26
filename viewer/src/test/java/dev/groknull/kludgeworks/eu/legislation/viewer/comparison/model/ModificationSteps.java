// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.WhoMadeAChange;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules about the modification of a step. They reach the domain only: {@link
 * Modification} applies the rules.
 */
public class ModificationSteps {
  /** An amending act. */
  private static final ActIdentifier ACT = new ActIdentifier("32019R0103");

  /** A corrigendum to it. */
  private static final ActIdentifier CORRIGENDUM = new ActIdentifier("32019R0103R(01)");

  private @Nullable Kind kind;
  private ActIdentifier by = ACT;
  private Modification tagged = Modification.AMENDED;
  private @Nullable Modification found;

  @Given("a difference where the later text {word} the occurrence")
  public void difference(String kind) {
    this.kind =
        switch (kind) {
          case "inserted" -> Kind.INSERTED;
          case "deleted" -> Kind.DELETED;
          case "changed" -> Kind.CHANGED;
          default -> throw new IllegalArgumentException("Not a kind of difference: " + kind);
        };
  }

  @Given("a step by {string} whose tag says {modification}")
  public void step(String by, Modification modification) {
    this.by =
        switch (by) {
          case "an act" -> ACT;
          case "a corrigendum" -> CORRIGENDUM;
          default -> throw new IllegalArgumentException("Not an act or a corrigendum: " + by);
        };
    tagged = modification;
  }

  @When("the step is the {word} step of the difference")
  public void position(String position) {
    var first = position.equals("first") || position.equals("only");
    var last = position.equals("last") || position.equals("only");
    found = WhoMadeAChange.modificationOfEachStep(kind(), first, last, by, tagged);
  }

  @When("the data names no act for the difference")
  public void noAct() {
    found = WhoMadeAChange.modificationWithoutAnAct(kind());
  }

  @When("Cellar types the amendment with the code {word}")
  public void cellar(String code) {
    found =
        WhoMadeAChange.modificationFromCellar(
            Amendment.Role.of(code)
                .orElseThrow(() -> new IllegalArgumentException("Not a code of Cellar: " + code)));
  }

  @Then("the modification is {modification}")
  public void modificationIs(Modification modification) {
    assertThat(found).isEqualTo(modification);
  }

  private Kind kind() {
    return Objects.requireNonNull(kind, "no difference given");
  }
}
