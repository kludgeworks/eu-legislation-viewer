// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.LegalText;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.StringReader;
import java.net.URL;
import java.util.Objects;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XPathCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.assertj3.XmlAssert;

/**
 * Normalises every fixture in scope (one document per Formex version and document type, see
 * {@link Fixtures#inScope()}) once, then checks what every legal text must be. Assertions name the
 * fixture, because Bazel's XML report names a test only by its method.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ParameterizedClass(name = "{0}")
@MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.Fixtures#inScope")
class NormaliserFixturesTest {

  static final Processor SAXON = new Processor(false);
  static final Normaliser NORMALISER = new Normaliser(SAXON);
  static final TextConservation TEXT = new TextConservation(SAXON);
  static final URL SCHEMA = resource("/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.xsd");
  static final JAXBContext JAXB = jaxb();
  static final String LIVE_DUPLICATE_EIDS =
      """
      let $ids := //(lt:unit | lt:quotedStructure | lt:note)[not(ancestor::lt:deleted)]/@eId/string()
      return distinct-values($ids[index-of($ids, .)[2]])
      """;

  @Parameter Fixture fixture;

  XdmNode legalText;
  String xml;

  @BeforeParameterizedClassInvocation
  void normalise(Fixture fixture) throws SaxonApiException {
    legalText = NORMALISER.normalise(fixture.source(), fixture.celex());
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

  /** Deleted placeholders keep their old eIds, which a later insertion may reuse; live units may not. */
  @Test
  void givesLiveUnitsUniqueEIds() throws SaxonApiException {
    var duplicates =
        xpath().evaluate(LIVE_DUPLICATE_EIDS, legalText).stream()
            .map(XdmItem::getStringValue)
            .toList();
    assertThat(duplicates).as("%s: eIds of more than one live unit", fixture).isEmpty();
  }

  @Test
  void keepsAllSourceText() {
    assertThat(TEXT.missing(fixture.source(), legalText)).as("%s: source text missing from the output", fixture).isEmpty();
  }

  private static XPathCompiler xpath() {
    var xpath = SAXON.newXPathCompiler();
    xpath.declareNamespace("lt", "https://kludgeworks.groknull.dev/ns/legal-text/1");
    return xpath;
  }

  private static JAXBContext jaxb() {
    try {
      return JAXBContext.newInstance(LegalText.class.getPackageName(), LegalText.class.getClassLoader());
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  private static URL resource(String name) {
    return Objects.requireNonNull(NormaliserFixturesTest.class.getResource(name), name);
  }
}
