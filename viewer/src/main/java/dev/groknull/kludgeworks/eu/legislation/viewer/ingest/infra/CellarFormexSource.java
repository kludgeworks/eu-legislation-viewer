// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarFailures;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexFiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.SourceUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * Reads the Formex of a document from Cellar. It reads the XML files of the latest Formex
 * manifestation one at a time as they come, and collects its image item metadata without fetching
 * image bytes. If Cellar does not answer, it throws {@link SourceUnavailableException}.
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
  public <T> Optional<Fetched<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read) {
    try {
      return files
          .manifestation(celex, language.code())
          .map(manifestation -> {
            var documents =
                manifestation.files().stream()
                    .filter(file -> file.name().endsWith(".xml"))
                    .map(file -> contentStreams.read(file.uri(), in -> read.apply(file.name(), in)))
                    .toList();
            var images =
                manifestation.files().stream()
                    .filter(file ->
                        !file.name().endsWith(".xml") && isImage(file.name(), file.mediaType()))
                    .map(file ->
                        new Images.Source(file.name(), file.uri(), file.mediaType(), file.sha1()))
                    .toList();
            return new Fetched<>(manifestation.uri(), documents, images);
          });
    } catch (RuntimeException e) {
      if (CellarFailures.unavailable(e)) {
        throw new SourceUnavailableException(
            "Cellar didn't answer for " + celex + " in " + language.code() + ": " + e, e);
      }
      throw e;
    }
  }

  private static boolean isImage(String name, String mediaType) {
    var lower = name.toLowerCase(Locale.ROOT);
    return mediaType.startsWith("image/") || lower.endsWith(".tif") || lower.endsWith(".tiff");
  }
}
