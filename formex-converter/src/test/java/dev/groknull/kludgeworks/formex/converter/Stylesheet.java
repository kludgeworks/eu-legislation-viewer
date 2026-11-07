// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * formex-to-legal-text.xsl run as any XSLT 3 client runs it, by its contract (the stylesheet's
 * header): the document as the global context item, the {@code celex} parameter, the document's,
 * refusals as errors in the converter's namespace. The tests use it, not the viewer's Java adapter,
 * so the stylesheets are checked on their own.
 *
 * <p>The JDK's XML entity limits are lifted for the documents Saxon parses: Formex has no DTD, and
 * a 104 MB annex has over 100,000 entity references (&amp;amp;).
 */
final class Stylesheet {

  /** The namespace of the stylesheet's error codes: f:out-of-scope, f:misfiled, f:unknown. */
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/formex-converter";

  static final QName OUT_OF_SCOPE = new QName(ERRORS, "out-of-scope");
  static final QName MISFILED = new QName(ERRORS, "misfiled");
  static final QName UNKNOWN = new QName(ERRORS, "unknown");

  private static final String PATH = "xslt/formex-to-legal-text.xsl";
  private static final QName CELEX = new QName("celex");

  private final Processor processor;
  private final XsltExecutable stylesheet;

  Stylesheet(Processor processor) {
    this.processor = processor;
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.maxGeneralEntitySizeLimit", "0");
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.totalEntitySizeLimit", "0");
    var url = Objects.requireNonNull(Stylesheet.class.getResource(PATH), PATH);
    try {
      stylesheet = processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException("formex-to-legal-text.xsl doesn't compile", e);
    }
  }

  /**
   * The legal text of {@code formex}, whose system id must be set.
   *
   * @throws Failure if the stylesheet refuses or fails
   */
  XdmNode transform(Source formex, Optional<String> celex) throws Failure {
    var transformer = stylesheet.load30();
    var result = new XdmDestination();
    try {
      if (celex.isPresent()) {
        transformer.setStylesheetParameters(Map.of(CELEX, new XdmAtomicValue(celex.get())));
      }
      var document = processor.newDocumentBuilder().build(formex);
      transformer.setGlobalContextItem(document);
      transformer.applyTemplates(document, result);
    } catch (SaxonApiException e) {
      throw new Failure(e.getErrorCode(), Objects.toString(e.getMessage()), e);
    }
    return result.getXdmNode();
  }

  /** The stylesheet stopped: its error code (f:out-of-scope, f:unknown, or Saxon's) and why. */
  static final class Failure extends Exception {
    private final @Nullable QName code;

    Failure(@Nullable QName code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }

    @Nullable QName code() {
      return code;
    }

    /** Why the stylesheet stopped: a Failure always says. */
    @Override
    public String getMessage() {
      return Objects.requireNonNull(super.getMessage());
    }
  }
}
