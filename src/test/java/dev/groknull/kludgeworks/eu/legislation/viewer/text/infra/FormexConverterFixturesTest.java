// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.LegalText;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;
import org.xmlunit.assertj3.XmlAssert;

/**
 * Converts every fixture in scope (one document per Formex version and document type, see {@link
 * Fixtures#inScope()}) once, then checks what every legal text must be. Assertions name the
 * fixture, because Bazel's XML report names a test only by its method.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ParameterizedClass(name = "{0}")
@MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.Fixtures#inScope")
class FormexConverterFixturesTest {

  static final Processor SAXON = new Processor(false);
  static final FormexConverter CONVERTER = new FormexConverter(SAXON);
  static final TextConservation TEXT = new TextConservation(SAXON);
  static final LegalTextRules RULES = new LegalTextRules(SAXON);
  static final Schema MATHML = mathml();
  static final URL SCHEMA = resource("/formex-converter/schema/legal-text.xsd");
  static final JAXBContext JAXB = jaxb();
  @Parameter Fixture fixture;

  XdmNode legalText;
  String xml;

  @BeforeParameterizedClassInvocation
  void convert(Fixture fixture) throws SaxonApiException {
    legalText = CONVERTER.convert(fixture.source(), fixture.celex());
    xml = SAXON.newSerializer().serializeNodeToString(legalText);
  }

  @Test
  void isValidAgainstTheSchema() {
    XmlAssert.assertThat(xml).as("%s", fixture).isValidAgainst(SCHEMA);
  }

  @Test
  void unmarshalsIntoTheGeneratedClasses() throws JAXBException {
    var unmarshalled = (JAXBElement<?>) JAXB.createUnmarshaller().unmarshal(new StringReader(xml));
    assertThat(unmarshalled.getValue()).as("%s", fixture).isInstanceOf(LegalText.class);
  }

  /** Unique live eIds, resolved note references, consistent amendment markers (legal-text.sch). */
  @Test
  void followsTheSchematronRules() {
    assertThat(RULES.violations(legalText)).as("%s: breaks legal-text.sch", fixture).isEmpty();
  }

  /** Formulas are MathML 3, valid against W3C's schema (legal-text.xsd takes them as they are). */
  @Test
  void writesValidMathML() throws SaxonApiException {
    var xpath = SAXON.newXPathCompiler();
    xpath.declareNamespace("m", "http://www.w3.org/1998/Math/MathML");
    var problems = new ArrayList<String>();
    for (var formula : xpath.evaluate("//m:math", legalText)) {
      try {
        MATHML.newValidator().validate(new StreamSource(new StringReader(formula.toString())));
      } catch (SAXException | IOException e) {
        problems.add(e.getMessage() + " in " + formula);
      }
    }
    assertThat(problems).as("%s: formulas that aren't valid MathML 3", fixture).isEmpty();
  }

  @Test
  void keepsAllSourceText() {
    assertThat(TEXT.missing(fixture.source(), legalText))
        .as("%s: source text missing from the output", fixture)
        .isEmpty();
  }

  private static Schema mathml() {
    try {
      return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
          .newSchema(resource("/mathml3/mathml3.xsd"));
    } catch (SAXException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JAXBContext jaxb() {
    try {
      return JAXBContext.newInstance(
          LegalText.class.getPackageName(), LegalText.class.getClassLoader());
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  private static URL resource(String name) {
    return Objects.requireNonNull(FormexConverterFixturesTest.class.getResource(name), name);
  }
}
