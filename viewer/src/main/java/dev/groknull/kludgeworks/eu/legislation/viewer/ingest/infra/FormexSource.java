// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Where a document's Formex comes from: the XML files of its package, for the converter to read.
 */
interface FormexSource {

  /**
   * What {@code read} makes of each XML file of the document {@code celex}'s Formex in {@code
   * language}, given the name its package refers to it by and its bytes, open while read reads
   * them.
   *
   * @param celex the act's CELEX for its original text, a consolidation's for a consolidated
   *     version
   * @return none where Cellar has no Formex of it in that language
   */
  <T> Optional<List<T>> fetch(
      String celex, Language language, BiFunction<String, InputStream, T> read);
}
