// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import io.cucumber.java.en.Given;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The comparison that a scenario is about: an act as Cellar's recorded answers make it, and two of
 * its versions, or two sample texts of an act that the recorded answers do not hold. The steps of
 * several rules share it: Cucumber (with PicoContainer) makes one for each scenario and gives it to
 * each step class that asks for it in its constructor.
 *
 * <p>The texts of the versions are the converter's sample texts, where it has them: 2015/1998 has
 * its original text and the texts of 29 February 2016, 16 February 2020, 2 July 2020, 15 March
 * 2021, 31 December 2021, and of 1 January, 22 March and 1 October 2026. Directive 2013/36 has the
 * texts of 17 January 2025 and of 11 January 2026.
 */
public class Comparing {
  private @Nullable Act act;
  private @Nullable Version from;
  private @Nullable Version to;
  private @Nullable Element earlier;
  private @Nullable Element later;

  @Given("the comparison of the texts of {act} of {day} and of {day}")
  public void comparison(ActIdentifier compared, LocalDate earlierDay, LocalDate laterDay) {
    act = RecordedActs.act(compared);
    from = version(act, earlierDay);
    to = version(act, laterDay);
  }

  /** Compares two sample texts, of an act that Cellar's recorded answers need not hold. */
  void texts(Element earlierText, Element laterText) {
    earlier = earlierText;
    later = laterText;
  }

  /** The act whose texts are compared. */
  Act act() {
    return Objects.requireNonNull(act, "no comparison given");
  }

  /** The earlier version. */
  Version from() {
    return Objects.requireNonNull(from, "no comparison given");
  }

  /** The later version. */
  Version to() {
    return Objects.requireNonNull(to, "no comparison given");
  }

  /** The earlier text: the one given, or else the text of the earlier version. */
  Element earlier() {
    return earlier != null ? earlier : text(from());
  }

  /** The later text: the one given, or else the text of the later version. */
  Element later() {
    return later != null ? later : text(to());
  }

  /** Returns whether the converter has a sample text of {@code version}. */
  static boolean hasText(Version version) {
    var name = version.celex() + ".xml";
    return LegalTexts.samples().anyMatch(sample -> sample.endsWith("/" + name));
  }

  /** The text of {@code version}, as the converter builds it from its sample. */
  static Element text(Version version) {
    return sample(version.celex());
  }

  /** The sample text of the consolidated version of {@code act} of {@code day}. */
  static Element consolidated(ActIdentifier act, LocalDate day) {
    return sample(
        "0" + act.celex().substring(1) + "-" + day.format(DateTimeFormatter.BASIC_ISO_DATE));
  }

  /** The sample text {@code name}, as the converter builds it. */
  static Element sample(String name) {
    var file = name + ".xml";
    var path =
        LegalTexts.samples()
            .filter(sample -> sample.endsWith("/" + file))
            .findFirst()
            .orElseThrow(() -> new AssertionError("The converter has no sample text " + name));
    return Decoder.element(Encoder.encode(LegalTexts.sample(path)));
  }

  /** Returns the eId of the one occurrence of {@code text} that is {@code place}. */
  static String eId(Place place, Occurrences text) {
    var found =
        Location.of(place)
            .in(List.copyOf(text.byEId().keySet()))
            .orElseThrow(() -> new AssertionError("The text has no " + place.words()));
    assertThat(found.eIds()).as(place.words()).hasSize(1);
    assertThat(found.within()).as(place.words()).isFalse();
    return found.eIds().getFirst();
  }

  private static Version version(Act act, LocalDate day) {
    return act.timeline().versions().stream()
        .filter(version -> version.date().equals(day))
        .findFirst()
        .orElseThrow(() -> new AssertionError(act.cited(act.id()) + " has no text of " + day));
  }
}
