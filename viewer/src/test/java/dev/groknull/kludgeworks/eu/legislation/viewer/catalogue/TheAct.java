// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import io.cucumber.java.en.Given;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The act of a scenario, as Cellar has it: its dates, the dates of the acts that amend it, and
 * their amendments. Cellar's answers are recorded ({@link RecordedActs}), so the rules run on real
 * data. The steps of the rules share it.
 */
public class TheAct {
  private @Nullable Act act;

  @Given("the act {act} as Cellar has it")
  public void cellar(ActIdentifier id) {
    act = RecordedActs.act(id);
  }

  /** The act of the scenario. */
  public Act act() {
    return Objects.requireNonNull(
        act, "No act: the scenario starts with \"the act … as Cellar has it\"");
  }
}
