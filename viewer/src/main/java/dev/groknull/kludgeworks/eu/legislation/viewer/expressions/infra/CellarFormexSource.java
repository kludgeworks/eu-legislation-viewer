// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.ContentStreams;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexFiles;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * A document's Formex from Cellar: the files of its latest Formex manifestation, fetched one at a
 * time as raw bytes. Only the XML: the converter reads no image. Which languages a document has
 * Formex in is kept for a while (ExpressionsCacheConfiguration).
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

  /** The official languages of those Cellar has Formex in: none other is a Language. */
  @Override
  @Cacheable(ExpressionsCacheConfiguration.LANGUAGES)
  public Set<Language> languages(String celex) {
    var codes = Set.copyOf(files.languages(celex));
    return Arrays.stream(Language.values())
        .filter(language -> codes.contains(language.code()))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Language.class)));
  }
}
