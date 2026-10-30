// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
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
 * Manifestations#fixtures()}) once, then checks what every legal text must be. Assertions name the
 * fixture, because Bazel's XML report names a test only by its method.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ParameterizedClass(name = "{0}")
@MethodSource("dev.groknull.kludgeworks.formex.converter.Manifestations#fixtures")
class FormexConverterFixturesTest {

  static final Processor SAXON = new Processor(false);
  static final Stylesheet STYLESHEET = new Stylesheet(SAXON);
  static final TextConservation TEXT = new TextConservation(SAXON);
  static final MetadataConservation METADATA = new MetadataConservation(SAXON);
  static final LegalTextRules RULES = new LegalTextRules(SAXON);
  static final Schema MATHML = mathml();
  static final URL SCHEMA = resource("schema/legal-text.xsd");
  static final CatalogResolver CATALOG =
      CatalogManager.catalogResolver(
          CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, "strict").build(),
          uri(resource("schema/catalog.xml")));
  @Parameter Manifestation fixture;

  XdmNode legalText;
  String xml;

  @BeforeParameterizedClassInvocation
  void convert(Manifestation fixture) throws SaxonApiException, Stylesheet.Failure {
    legalText = STYLESHEET.transform(fixture.source(), fixture.celex());
    xml = SAXON.newSerializer().serializeNodeToString(legalText);
  }

  @Test
  void isValidAgainstTheSchema() {
    XmlAssert.assertThat(xml).as("%s", fixture).isValidAgainst(SCHEMA);
  }

  /**
   * The schema the document names (xsi:schemaLocation) resolves through catalog.xml to the copy on
   * the classpath; strictly, so a URL the catalog doesn't know fails instead of being fetched.
   */
  @Test
  void isValidAgainstTheSchemaItNames() throws SAXException, IOException {
    var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setResourceResolver(CATALOG);
    var validator = factory.newSchema().newValidator();
    validator.setResourceResolver(CATALOG);
    validator.validate(new StreamSource(new StringReader(xml)));
  }

  /** The output is what formex-converter/src/test/resources/expected holds for the fixture. */
  @Test
  void matchesTheExpectedOutput() throws IOException, SaxonApiException {
    var expected = ExpectedOutput.of(fixture);
    assertThat(expected).as("%s: no expected output; run mise run expected", fixture).exists();
    XmlAssert.assertThat(ExpectedOutput.serialize(SAXON, legalText))
        .and(Files.readString(expected, StandardCharsets.UTF_8))
        .ignoreWhitespace()
        .areIdentical();
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

  @Test
  void keepsTheMetadataTheViewerReliesOn() {
    assertThat(METADATA.missing(fixture.source(), legalText))
        .as("%s: source metadata missing from the output", fixture)
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

  private static URI uri(URL url) {
    try {
      return url.toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static URL resource(String name) {
    return Objects.requireNonNull(FormexConverterFixturesTest.class.getResource(name), name);
  }
}
