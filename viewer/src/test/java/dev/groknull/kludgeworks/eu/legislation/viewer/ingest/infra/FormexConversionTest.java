// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.FormexFixtures.CONSOLIDATION;
import static net.sf.saxon.s9api.streams.Predicates.isElement;
import static net.sf.saxon.s9api.streams.Steps.child;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests that {@link FormexConversion} converts the Formex of a document into its legal text. The
 * Formex comes from the source, and the real converter converts it. Here the source is the
 * converter's fixtures. They include the consolidation of 2015/1998 of 22 March 2026.
 */
class FormexConversionTest {
  static final Processor SAXON = new Processor(false);

  final FormexFixtures source = new FormexFixtures();
  final FormexConversion conversion = new FormexConversion(source, new FormexConverter(SAXON));

  @Test
  void convertsTheDocumentsFormex() {
    var xml = conversion.convert(CONSOLIDATION, Language.ENG).orElseThrow();
    var legalText = xml.select(child(isElement())).asNode();

    assertThat(legalText.attribute("act")).isEqualTo("32015R1998");
    assertThat(legalText.attribute("kind")).isEqualTo("consolidated");
    assertThat(legalText.attribute("version")).isEqualTo("2026-03-22");
    assertThat(legalText.attribute("converter")).isEqualTo(conversion.converterVersion());
    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  /**
   * Tests that the files of a package, read from the source, give the same result as the converter
   * gives from them on disk. That result is the converter's expected output. For 2024/2867 the
   * files are the descriptor, the act, the annex and the table of contents.
   */
  @ParameterizedTest
  @ValueSource(strings = {CONSOLIDATION, "32024R2867"})
  void givesWhatTheConverterGivesFromTheFilesOnDisk(String document) throws SaxonApiException {
    var xml = conversion.convert(document, Language.ENG).orElseThrow();

    var serializer = SAXON.newSerializer();
    serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
    serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");
    assertThat(serializer.serializeNodeToString(xml) + "\n")
        .isEqualTo(FormexFixtures.expected(document));
  }

  @Test
  void hasNoTextInALanguageWithoutFormex() {
    assertThat(conversion.convert(CONSOLIDATION, Language.GLE)).isEmpty();
  }
}
