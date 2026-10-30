// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexFiles;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * A document's Formex from Cellar: the files of its latest Formex manifestation, fetched one at a
 * time as raw bytes. Only the XML: the converter reads no image.
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
  public boolean fetch(String celex, Language language, Path folder) {
    var manifestation = files.manifestation(celex, language.code());
    manifestation.ifPresent(
        m ->
            m.files().stream()
                .filter(file -> !file.mediaType().startsWith("image/"))
                .forEach(
                    file ->
                        contentStreams.download(file.uri(), null, folder.resolve(file.name()))));
    return manifestation.isPresent();
  }
}
