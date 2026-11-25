// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.List;

/**
 * The steps of the rules of the versions of an act: its timeline in a language, as {@link
 * Act#timeline} gives it, version by version from the first.
 */
public class VersionsSteps {
  private final TheAct the;
  private List<Version> versions = List.of();
  private int at;

  public VersionsSteps(TheAct the) {
    this.the = the;
  }

  @Then("in {language}, the first version is the original text of {day}")
  public void first(Language language, LocalDate day) {
    versions = the.act().timeline(language).orElseThrow().versions();
    at = 0;
    assertThat(versions).as("the versions").isNotEmpty();
    assertThat(versions.getFirst().kind()).as("the first version").isEqualTo(Version.Kind.ORIGINAL);
    assertThat(versions.getFirst().date()).as("the first version").isEqualTo(day);
  }

  @Then("the next version is the consolidation of {day}")
  public void next(LocalDate day) {
    at++;
    assertThat(versions.size()).as("a version after version %d", at).isGreaterThan(at);
    var version = versions.get(at);
    assertThat(version.kind()).as("version %d", at).isEqualTo(Version.Kind.CONSOLIDATED);
    assertThat(version.date()).as("version %d", at).isEqualTo(day);
  }
}
