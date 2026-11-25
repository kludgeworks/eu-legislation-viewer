// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Given;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  @Given("the viewer builds its texts where the converter has a sample")
  public void built() {
    texts = SampleTexts.of(act(), null);
  }

  @Given("the viewer builds its texts where the converter has a sample, except the text of {day}")
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

  /** What the dates of {@code id} tell: the act of the scenario, or an act that amends it. */
  public DatedAct dated(ActIdentifier id) {
    return DatedAct.of(act(), id);
  }

  /** What the rules tell of the act of the scenario, for each day. */
  public ActOverTime overTime(Language language) {
    return ActOverTime.of(act(), language);
  }

  /** The amending acts of the act of the scenario, newest first. */
  public List<ActIdentifier> amendingActs(Language language) {
    return overTime(language).amendingActs().stream().map(ActOverTime.Amending::act).toList();
  }

  /** The tags of the latest English text that is built, or empty if none is. */
  public Optional<TextTags> latest() {
    return TheHistoryOfAnAct.latestBuiltText(act(), Language.ENG, texts);
  }

  /** The changes of {@code amending}, in the order of the data. */
  public List<Amendment> changes(ActIdentifier amending, Language language) {
    return TheHistoryOfAnAct.changesOfAnAmendingAct(act(), amending, language, null);
  }

  /** The changes of {@code amending} to the English text, in the order of the text. */
  public List<Amendment> changesInTextOrder(ActIdentifier amending) {
    return TheHistoryOfAnAct.changesOfAnAmendingAct(
        act(), amending, Language.ENG, latest().orElse(null));
  }

  /** The amending acts between two days, by the English texts that are built. */
  public Optional<List<ActIdentifier>> between(LocalDate from, LocalDate to) {
    var law = LawOverTime.of(act(), Language.ENG, texts);
    return TheHistoryOfAnAct.amendingActsBetweenTwoDays(
        act(), law.on(from).shown(), law.on(to).shown(), amendingActs(Language.ENG));
  }
}
