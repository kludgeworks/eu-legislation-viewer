// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;

/** The conversion of the Formex of texts into legal texts. The infrastructure provides it. */
public interface Conversions {

  /**
   * Returns the converted legal-text XML and image sources of a text.
   *
   * @return none if Cellar has no Formex of the text
   * @throws ConversionException if its Formex cannot be converted. Its subclasses include {@code
   *     OutOfScopeException} and {@code UnsupportedFormexException}.
   */
  Optional<Converted> convert(String celex, Language language);

  /** Returns the version of the converter that makes the legal texts. */
  String converterVersion();

  /**
   * Result of converting a Formex manifestation.
   *
   * @param legalText the converted legal-text XML
   * @param manifestation the Cellar manifestation URI
   * @param images the image sources found in the manifestation
   */
  record Converted(XdmNode legalText, URI manifestation, List<Images.Source> images) {}
}
