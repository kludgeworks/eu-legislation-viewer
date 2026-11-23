// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The texts of the versions of an act, as the converter builds them from its samples (its fixtures
 * and their expected output, {@link LegalTexts}). A version without a sample has no built text, as
 * a version whose text the viewer has not built yet.
 */
final class SampleTexts {
  private SampleTexts() {}

  /**
   * Returns the tags of the texts of the versions of {@code act} that the converter has a sample
   * of, by version. The text of the version of {@code except} is left out, if it is given.
   */
  static Map<Version, TextTags> of(Act act, @Nullable LocalDate except) {
    var texts = new LinkedHashMap<Version, TextTags>();
    for (var version : act.timeline().versions()) {
      if (version.date().equals(except)) {
        continue;
      }
      var file = "/" + version.celex() + ".xml";
      LegalTexts.samples()
          .filter(sample -> sample.endsWith(file))
          .findFirst()
          .ifPresent(
              sample ->
                  texts.put(
                      version,
                      TextTags.of(
                          Outlines.of(
                              Decoder.element(Encoder.encode(LegalTexts.sample(sample)))))));
    }
    return texts;
  }
}
