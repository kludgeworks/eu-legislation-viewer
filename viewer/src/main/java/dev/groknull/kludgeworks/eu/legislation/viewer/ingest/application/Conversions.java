// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;

/** Expressions' legal texts converted from their Formex: what the infrastructure provides. */
public interface Conversions {

  /**
   * The legal-text XML of an expression.
   *
   * @return none if Cellar has no Formex of it in the language
   * @throws ConversionException if its Formex can't be converted (OutOfScopeException,
   *     UnsupportedFormexException among them)
   */
  Optional<XdmNode> convert(String celex, Language language);

  /** The converter's version: what the legal texts are made with. */
  String converterVersion();
}
