// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * The comparison that a scenario is about: an act as Cellar's recorded answers make it, and two of
 * its versions. The steps of several rules share it. Each scenario starts with none.
 *
 * <p>The texts of the versions are the converter's sample texts, where it has them: 2015/1998 has
 * its original text and the texts of 29 February 2016, 16 February 2020, 31 December 2021, and of 1
 * January, 22 March and 1 October 2026. Directive 2013/36 has the texts of 17 January 2025 and of
 * 11 January 2026.
 */
public class Comparing {
  /** The comparison of the scenario that runs, or null before its first step gives one. */
  private static final AtomicReference<@Nullable Compared> COMPARED = new AtomicReference<>();

  private record Compared(Act act, Version from, Version to) {}

  @Before
  public void forget() {
    COMPARED.set(null);
  }

  @Given("the comparison of the texts of {act} of {day} and of {day}")
  public void comparison(ActIdentifier compared, LocalDate earlier, LocalDate later) {
    var act = RecordedActs.act(compared);
    COMPARED.set(new Compared(act, version(act, earlier), version(act, later)));
  }

  /** The act whose texts are compared. */
  static Act act() {
    return compared().act();
  }

  /** The earlier version. */
  static Version from() {
    return compared().from();
  }

  /** The later version. */
  static Version to() {
    return compared().to();
  }

  private static Compared compared() {
    return Objects.requireNonNull(COMPARED.get(), "no comparison given");
  }

  /** The text of {@code version}, as the converter builds it from its sample. */
  static Element text(Version version) {
    var name = version.celex() + ".xml";
    var path =
        LegalTexts.samples()
            .filter(sample -> sample.endsWith("/" + name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("The converter has no sample text " + name));
    return Decoder.element(Encoder.encode(LegalTexts.sample(path)));
  }

  private static Version version(Act act, LocalDate day) {
    return act.timeline().versions().stream()
        .filter(version -> version.date().equals(day))
        .findFirst()
        .orElseThrow(() -> new AssertionError(act.cited(act.id()) + " has no text of " + day));
  }
}
