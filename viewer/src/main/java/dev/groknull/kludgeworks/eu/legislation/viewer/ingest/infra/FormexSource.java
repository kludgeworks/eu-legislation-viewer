// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The source of the Formex of a document. It gives the XML files of its package to the converter.
 */
interface FormexSource {

  /**
   * Applies {@code read} to each XML file of the Formex of the document {@code celex} in {@code
   * language}, and returns the results. {@code read} gets the name that the package uses for the
   * file, and the bytes of the file. The stream stays open while {@code read} reads it.
   *
   * @param celex the CELEX of the act for its original text, or the CELEX of a consolidation for a
   *     consolidated version
   * @return none if Cellar has no Formex of the document in that language
   */
  <T> Optional<List<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read);
}
