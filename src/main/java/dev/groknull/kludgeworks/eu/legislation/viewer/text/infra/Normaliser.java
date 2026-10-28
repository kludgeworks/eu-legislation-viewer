// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Turns Formex 4 into legal-text XML with {@code normalise.xsl}. The stylesheet is compiled once;
 * each call gets its own transformer, so one instance serves concurrent calls.
 *
 * <p>Formex files can be large: an annex of 104 MB with over 100,000 entity references (&amp;amp;).
 * Since JDK 24 the JDK's XML parser stops at 100,000 ({@code jdk.xml.maxGeneralEntitySizeLimit}),
 * so the constructor lifts that limit on the processor it is given, for the documents Saxon parses:
 * the input when it is a stream, and the files the stylesheet reads. Formex has no DTD, so there
 * are no entities to expand beyond the predefined ones.
 */
public final class Normaliser {

  static final String STYLESHEET =
      "/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xslt/normalise.xsl";
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/normalise";
  private static final QName CELEX = new QName("celex");

  private final XsltExecutable stylesheet;

  public Normaliser(Processor processor) {
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.maxGeneralEntitySizeLimit", "0");
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.totalEntitySizeLimit", "0");
    var url = Objects.requireNonNull(Normaliser.class.getResource(STYLESHEET), STYLESHEET);
    try {
      stylesheet = processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException("normalise.xsl doesn't compile", e);
    }
  }

  /**
   * Normalises one document.
   *
   * @param formex a consolidation (CONS.ACT, CONS.OTH), an OJ act (ACT, AGR, CORR) or its package
   *     descriptor (DOC). Its system id must be set: the files it refers to (annexes, included
   *     documents) are resolved against it.
   * @param celex the CELEX number of an OJ act, which Formex doesn't carry; empty for a
   *     consolidation, which carries its own
   * @return the legal-text document
   * @throws OutOfScopeException if the document isn't a legal act (notices, case law, the OJ's
   *     tables of contents)
   * @throws UnsupportedFormexException if it uses Formex the normaliser doesn't handle yet
   * @throws NormalisationException if it can't be normalised for another reason
   */
  public XdmNode normalise(Source formex, Optional<String> celex) {
    var transformer = stylesheet.load30();
    // a refusal is an xsl:message that terminates: its text is the reason, Saxon's own message
    // isn't
    var reason = new AtomicReference<String>();
    transformer.setMessageHandler(
        m -> reason.set(m.isTerminate() ? m.getStringValue() : reason.get()));
    var result = new XdmDestination();
    try {
      if (celex.isPresent()) {
        transformer.setStylesheetParameters(Map.of(CELEX, new XdmAtomicValue(celex.get())));
      }
      transformer.applyTemplates(formex, result);
    } catch (SaxonApiException e) {
      throw translate(e, Optional.ofNullable(reason.get()).orElse(e.getMessage()));
    }
    return result.getXdmNode();
  }

  private static NormalisationException translate(SaxonApiException e, String message) {
    var code = e.getErrorCode();
    if (code != null && ERRORS.equals(code.getNamespaceURI())) {
      return switch (code.getLocalName()) {
        case "out-of-scope" -> new OutOfScopeException(message, e);
        case "unknown" -> new UnsupportedFormexException(message, e);
        default -> new NormalisationException(message, e);
      };
    }
    return new NormalisationException(message, e);
  }
}
