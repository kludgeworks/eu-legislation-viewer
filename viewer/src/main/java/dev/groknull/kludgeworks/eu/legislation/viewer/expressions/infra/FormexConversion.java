// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.LegalTextIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.XdmNode;
import org.springframework.stereotype.Component;

/**
 * A document's legal text converted from its Formex: the Formex in the language is fetched into a
 * folder of its own, converted, and the folder deleted. Every call converts; ValkeyLegalTexts keeps
 * the result.
 */
@Component
class FormexConversion {
  private final FormexSource source;
  private final FormexConverter converter;

  FormexConversion(FormexSource source, FormexConverter converter) {
    this.source = source;
    this.converter = converter;
  }

  /**
   * The legal-text XML of {@code id}'s document in its language.
   *
   * @return none if Cellar has no Formex of the document in the language
   */
  Optional<XdmNode> convert(LegalTextIdentifier id) {
    var folder = folder();
    try {
      if (!source.fetch(id.document(), id.language(), folder)) {
        return Optional.empty();
      }
      var formex = new StreamSource(FormexConverter.input(folder).toFile());
      return Optional.of(converter.convert(formex, Optional.of(id.document())));
    } finally {
      delete(folder);
    }
  }

  /** The converter's version: what the legal text is made with, so part of its key in the store. */
  String converterVersion() {
    return converter.version();
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
