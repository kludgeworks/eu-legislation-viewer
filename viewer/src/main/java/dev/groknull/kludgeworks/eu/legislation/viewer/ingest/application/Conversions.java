// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;

/** The conversion of the Formex of texts into legal texts. The infrastructure provides it. */
public interface Conversions {

  /**
   * Returns the legal-text XML of a text.
   *
   * @return none if Cellar has no Formex of the text
   * @throws ConversionException if its Formex cannot be converted. Its subclasses include {@code
   *     OutOfScopeException} and {@code UnsupportedFormexException}.
   */
  Optional<XdmNode> convert(String celex, Language language);

  /** Returns the version of the converter that makes the legal texts. */
  String converterVersion();
}
