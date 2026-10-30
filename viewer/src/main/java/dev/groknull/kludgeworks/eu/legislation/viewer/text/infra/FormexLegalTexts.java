// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTexts;
import dev.groknull.kludgeworks.formex.converter.FormexConverter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import org.springframework.stereotype.Component;

/**
 * Legal texts converted from Formex when asked for: acts chooses the version, its Formex is fetched
 * into a folder of its own, converted and read into the records, and the folder deleted. Nothing is
 * kept yet; the store comes next (Design: Caching, Converting off the request path).
 */
@Component
class FormexLegalTexts implements LegalTexts {
  private final Acts acts;
  private final FormexSource source;
  private final FormexConverter converter;
  private final LegalTextReader reader;

  FormexLegalTexts(
      Acts acts, FormexSource source, FormexConverter converter, LegalTextReader reader) {
    this.acts = acts;
    this.source = source;
    this.converter = converter;
    this.reader = reader;
  }

  @Override
  public Optional<LegalText> find(ActIdentifier act, LocalDate date, Language language) {
    return acts.find(act)
        .flatMap(a -> a.versionOn(date))
        .flatMap(version -> text(version, language));
  }

  private Optional<LegalText> text(Version version, Language language) {
    var folder = folder();
    try {
      if (!source.fetch(version.celex(), language, folder)) {
        return Optional.empty();
      }
      var formex = new StreamSource(FormexConverter.input(folder).toFile());
      return Optional.of(reader.read(converter.convert(formex, Optional.of(version.celex()))));
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
