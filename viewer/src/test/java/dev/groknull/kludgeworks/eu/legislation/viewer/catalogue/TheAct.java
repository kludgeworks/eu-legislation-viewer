// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import io.cucumber.java.en.Given;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The act of a scenario, as Cellar has it: its dates, the dates of the acts that amend it, and
 * their amendments. Cellar's answers are recorded ({@link RecordedActs}), so the rules run on real
 * data. With them, the tags of the texts of its versions that are built ({@link SampleTexts}): none
 * until a step builds them. The steps of the rules share it.
 */
public class TheAct {
  private @Nullable Act act;
  private Map<Version, TextTags> texts = Map.of();

  @Given("the act {act} as Cellar has it")
  public void cellar(ActIdentifier id) {
    act = RecordedActs.act(id);
  }

  @Given("its texts are built where the converter has a sample")
  public void built() {
    texts = SampleTexts.of(act(), null);
  }

  @Given("its texts are built where the converter has a sample, except the text of {day}")
  public void builtExcept(LocalDate day) {
    texts = SampleTexts.of(act(), day);
  }

  /** The act of the scenario. */
  public Act act() {
    return Objects.requireNonNull(
        act, "No act: the scenario starts with \"the act … as Cellar has it\"");
  }

  /** The tags of the texts of the act's versions that are built, by version. */
  public Map<Version, TextTags> texts() {
    return texts;
  }
}
