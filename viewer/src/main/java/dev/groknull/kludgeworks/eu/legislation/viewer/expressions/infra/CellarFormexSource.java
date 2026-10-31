// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexFiles;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * A document's Formex from Cellar: the files of its latest Formex manifestation, read one at a time
 * as they come. Only the XML: the converter reads no image.
 */
@Component
class CellarFormexSource implements FormexSource {
  private final FormexFiles files;
  private final ContentStreams contentStreams;

  CellarFormexSource(FormexFiles files, ContentStreams contentStreams) {
    this.files = files;
    this.contentStreams = contentStreams;
  }

  @Override
  public <T> Optional<List<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read) {
    return files
        .manifestation(celex, language.code())
        .map(
            manifestation ->
                manifestation.files().stream()
                    .filter(file -> file.name().endsWith(".xml"))
                    .map(file -> contentStreams.read(file.uri(), in -> read.apply(file.name(), in)))
                    .toList());
  }
}
