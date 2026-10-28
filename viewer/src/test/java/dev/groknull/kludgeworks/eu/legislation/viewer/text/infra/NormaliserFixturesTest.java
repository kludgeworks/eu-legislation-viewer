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
import net.sf.saxon.s9api.XdmNode;
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
  static final LegalTextRules RULES = new LegalTextRules(SAXON);
  static final URL SCHEMA = resource("/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.xsd");
  static final JAXBContext JAXB = jaxb();
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

  /** Unique live eIds, resolved note references, consistent amendment markers (legal-text.sch). */
  @Test
  void followsTheSchematronRules() {
    assertThat(RULES.violations(legalText)).as("%s: breaks legal-text.sch", fixture).isEmpty();
  }

  @Test
  void keepsAllSourceText() {
    assertThat(TEXT.missing(fixture.source(), legalText)).as("%s: source text missing from the output", fixture).isEmpty();
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
