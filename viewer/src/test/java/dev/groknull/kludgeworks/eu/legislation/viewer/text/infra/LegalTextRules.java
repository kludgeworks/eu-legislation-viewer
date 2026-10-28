// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import java.util.List;
import java.util.Objects;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * legal-text.sch, the rules legal-text.xsd can't express, compiled with SchXslt 2 (its
 * transpiler turns the Schematron into an XSLT validator that reports in SVRL).
 */
final class LegalTextRules {

  static final String SCHEMATRON = "/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.sch";
  private static final String TRANSPILER = "/content/transpile.xsl";

  /** A rule a legal text breaks: the assertion's id, where, and its message. */
  record Violation(String rule, String location, String message) {
    @Override
    public String toString() {
      return rule + " at " + location + ": " + message;
    }
  }

  private final XsltExecutable validator;
  private final XPathCompiler svrl;

  LegalTextRules(Processor processor) {
    try {
      var transpiler = processor.newXsltCompiler().compile(new StreamSource(resource(TRANSPILER)));
      var compiled = new XdmDestination();
      transpiler.load30().applyTemplates(new StreamSource(resource(SCHEMATRON)), compiled);
      validator = processor.newXsltCompiler().compile(compiled.getXdmNode().asSource());
    } catch (SaxonApiException e) {
      throw new IllegalStateException("legal-text.sch doesn't compile", e);
    }
    svrl = processor.newXPathCompiler();
    svrl.declareNamespace("svrl", "http://purl.oclc.org/dsdl/svrl");
  }

  /** The rules the legal text breaks, in document order; empty when it follows them all. */
  List<Violation> violations(XdmNode legalText) {
    try {
      var report = new XdmDestination();
      validator.load30().applyTemplates(legalText, report);
      return svrl.evaluate("//(svrl:failed-assert | svrl:successful-report)", report.getXdmNode()).stream()
          .map(XdmNode.class::cast)
          .map(v -> new Violation(v.attribute("id"), v.attribute("location"), text(v)))
          .toList();
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private String text(XdmNode violation) {
    try {
      return svrl.evaluateSingle("normalize-space(svrl:text)", violation).getStringValue();
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String resource(String name) {
    return Objects.requireNonNull(LegalTextRules.class.getResource(name), name).toExternalForm();
  }
}
