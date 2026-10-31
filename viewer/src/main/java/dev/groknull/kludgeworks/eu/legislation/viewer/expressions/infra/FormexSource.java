// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.nio.file.Path;
import java.util.Set;

/** Where a document's Formex comes from: its files, for the converter to read. */
interface FormexSource {

  /**
   * Writes the Formex files of the document {@code celex} in {@code language} into {@code folder},
   * each named as its package refers to it.
   *
   * @param celex the act's CELEX for its original text, a consolidation's for a consolidated
   *     version
   * @return whether there are any: false where Cellar has no Formex of it in that language
   */
  boolean fetch(String celex, Language language, Path folder);

  /** The languages the document {@code celex} has Formex in; none if it has none. */
  Set<Language> languages(String celex);
}
