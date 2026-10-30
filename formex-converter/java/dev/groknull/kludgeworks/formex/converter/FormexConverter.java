// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

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
import org.jspecify.annotations.Nullable;

/**
 * Turns Formex 4 into legal-text XML with {@code formex-to-legal-text.xsl}. The stylesheet is
 * compiled once; each call gets its own transformer, so one instance serves concurrent calls.
 *
 * <p>Formex files can be large: an annex of 104 MB with over 100,000 entity references (&amp;amp;).
 * Since JDK 24 the JDK's XML parser stops at 100,000 ({@code jdk.xml.maxGeneralEntitySizeLimit}),
 * so the constructor lifts that limit on the processor it is given, for the documents Saxon parses:
 * the input when it is a stream, and the files the stylesheet reads. Formex has no DTD, so there
 * are no entities to expand beyond the predefined ones.
 */
public final class FormexConverter {

  static final String STYLESHEET = "/formex-converter/xslt/formex-to-legal-text.xsl";
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/formex-converter";
  private static final QName CELEX = new QName("celex");

  private final Processor processor;
  private final XsltExecutable stylesheet;

  public FormexConverter(Processor processor) {
    this.processor = processor;
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.maxGeneralEntitySizeLimit", "0");
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.totalEntitySizeLimit", "0");
    var url = Objects.requireNonNull(FormexConverter.class.getResource(STYLESHEET), STYLESHEET);
    try {
      stylesheet = processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException("formex-to-legal-text.xsl doesn't compile", e);
    }
  }

  /**
   * Converts one document.
   *
   * @param formex a consolidation (CONS.ACT, CONS.OTH), an OJ act (ACT, AGR, CORR) or its package
   *     descriptor (DOC). Its system id must be set: the files it refers to (annexes, included
   *     documents) are resolved against it.
   * @param celex the CELEX number of an OJ act, which Formex doesn't carry; empty for a
   *     consolidation, which carries its own
   * @return the legal-text document
   * @throws OutOfScopeException if the document isn't a legal act (notices, case law, the OJ's
   *     tables of contents)
   * @throws UnsupportedFormexException if it uses Formex the converter doesn't handle yet
   * @throws ConversionException if it can't be converted for another reason
   */
  public XdmNode convert(Source formex, Optional<String> celex) {
    var transformer = stylesheet.load30();
    // a refusal is an xsl:message that terminates: its text is the reason, Saxon's own message
    // isn't
    var reason = new AtomicReference<@Nullable String>();
    transformer.setMessageHandler(
        m -> reason.set(m.isTerminate() ? m.getStringValue() : reason.get()));
    var result = new XdmDestination();
    try {
      if (celex.isPresent()) {
        transformer.setStylesheetParameters(Map.of(CELEX, new XdmAtomicValue(celex.get())));
      }
      // the document is the global context item too, as Saxon's command line has it: the
      // stylesheet's global variables read it (the package's files, its amendment markers)
      var document = processor.newDocumentBuilder().build(formex);
      transformer.setGlobalContextItem(document);
      transformer.applyTemplates(document, result);
    } catch (SaxonApiException e) {
      var refusal = reason.get();
      throw translate(e, refusal != null ? refusal : String.valueOf(e.getMessage()));
    }
    return result.getXdmNode();
  }

  private static ConversionException translate(SaxonApiException e, String message) {
    var code = e.getErrorCode();
    if (code != null && ERRORS.equals(code.getNamespaceURI())) {
      return switch (code.getLocalName()) {
        case "out-of-scope" -> new OutOfScopeException(message, e);
        case "unknown" -> new UnsupportedFormexException(message, e);
        default -> new ConversionException(message, e);
      };
    }
    return new ConversionException(message, e);
  }
}
