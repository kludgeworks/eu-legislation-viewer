// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import dev.groknull.kludgeworks.build.Schematron;
import java.util.List;
import java.util.Objects;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/** legal-text.sch, the rules legal-text.xsd can't express. */
final class LegalTextRules {

  static final String SCHEMATRON = "/formex-converter/schema/legal-text.sch";

  private final Schematron schema;

  LegalTextRules(Processor saxon) {
    try {
      schema =
          Schematron.compile(
              saxon,
              Objects.requireNonNull(LegalTextRules.class.getResource(SCHEMATRON), SCHEMATRON)
                  .toExternalForm());
    } catch (SaxonApiException e) {
      throw new IllegalStateException("legal-text.sch doesn't compile", e);
    }
  }

  /** The rules the legal text breaks, in document order; empty when it follows them all. */
  List<Schematron.Finding> violations(XdmNode legalText) {
    try {
      return schema.validate(legalText.asSource());
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }
}
