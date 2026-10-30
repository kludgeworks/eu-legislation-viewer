// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTextIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTexts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import org.springframework.stereotype.Component;

/**
 * Legal texts converted from Formex when asked for: the document's Formex in the language is
 * fetched into a folder of its own, converted and read into the records, and the folder deleted.
 * Nothing is kept yet; the store comes next (Design: Caching, Converting off the request path).
 */
@Component
class FormexLegalTexts implements LegalTexts {
  private final FormexSource source;
  private final FormexConverter converter;
  private final LegalTextReader reader;

  FormexLegalTexts(FormexSource source, FormexConverter converter, LegalTextReader reader) {
    this.source = source;
    this.converter = converter;
    this.reader = reader;
  }

  @Override
  public Optional<LegalText> find(LegalTextIdentifier id) {
    var folder = folder();
    try {
      if (!source.fetch(id.document(), id.language(), folder)) {
        return Optional.empty();
      }
      var formex = new StreamSource(FormexConverter.input(folder).toFile());
      return Optional.of(reader.read(converter.convert(formex, Optional.of(id.document()))));
    } finally {
      delete(folder);
    }
  }

  private static Path folder() {
    try {
      return Files.createTempDirectory("formex-");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void delete(Path folder) {
    try (var paths = Files.walk(folder)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
