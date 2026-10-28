// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * An ISO Schematron schema compiled with SchXslt 2, whose transpiler turns the schema into an XSLT
 * validator that reports in SVRL. A finding is a failed assertion or a successful report.
 */
public final class Schematron {
  private static final String TRANSPILER = "/content/transpile.xsl";
  private static final String SVRL = "http://purl.oclc.org/dsdl/svrl";

  /** What the schema says about a document: the rule's id, its role, where, and its message. */
  public record Finding(String id, String role, String location, String text) {
    @Override
    public String toString() {
      return (role.isEmpty() ? "" : role + " ") + id + " at " + location + ": " + text;
    }
  }

  private final XsltExecutable validator;
  private final XPathCompiler svrl;

  private Schematron(XsltExecutable validator, XPathCompiler svrl) {
    this.validator = validator;
    this.svrl = svrl;
  }

  /**
   * Compiles the schema at {@code uri}. The validator gets the schema's URI as its base, so the
   * stylesheets the schema includes resolve from where the schema is.
   */
  public static Schematron compile(Processor saxon, String uri) throws SaxonApiException {
    var transpiler = saxon.newXsltCompiler().compile(new StreamSource(resource(TRANSPILER)));
    var validator = new StringWriter();
    transpiler.load30().transform(new StreamSource(uri), saxon.newSerializer(validator));
    var svrl = saxon.newXPathCompiler();
    svrl.declareNamespace("svrl", SVRL);
    return new Schematron(
        saxon
            .newXsltCompiler()
            .compile(new StreamSource(new StringReader(validator.toString()), uri)),
        svrl);
  }

  /** The findings for {@code document}, in document order; empty when it follows every rule. */
  public List<Finding> validate(Source document) throws SaxonApiException {
    return validate(document, Map.of());
  }

  /** The same, with values for the validator's parameters (a schema's configuration). */
  public List<Finding> validate(Source document, Map<QName, XdmValue> parameters)
      throws SaxonApiException {
    var transformer = validator.load30();
    transformer.setStylesheetParameters(parameters);
    var report = new XdmDestination();
    transformer.transform(document, report);
    return svrl
        .evaluate("//(svrl:failed-assert | svrl:successful-report)", report.getXdmNode())
        .stream()
        .map(XdmNode.class::cast)
        .map(this::finding)
        .toList();
  }

  private Finding finding(XdmNode node) {
    try {
      return new Finding(
          Objects.toString(node.attribute("id"), ""),
          Objects.toString(node.attribute("role"), ""),
          Objects.toString(node.attribute("location"), ""),
          svrl.evaluateSingle("normalize-space(svrl:text)", node).getStringValue());
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String resource(String name) {
    return Objects.requireNonNull(Schematron.class.getResource(name), name).toExternalForm();
  }
}
