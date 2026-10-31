// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra.FormexFixtures.CONSOLIDATION;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.time.LocalDate;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A document's legal text converted from its Formex: from the source, by the real converter; here
 * the source is the converter's fixtures, 2015/1998's consolidation of 22 March 2026 among them.
 */
class FormexConversionTest {
  static final Processor SAXON = new Processor(false);

  final FormexFixtures source = new FormexFixtures();
  final FormexConversion conversion = new FormexConversion(source, new FormexConverter(SAXON));

  @Test
  void convertsTheDocumentsFormex() {
    var xml =
        conversion.convert(new ExpressionIdentifier(CONSOLIDATION, Language.ENG)).orElseThrow();
    var text = new LegalTextReader(SAXON).read(xml);

    assertThat(text.id()).isEqualTo(new ExpressionIdentifier(CONSOLIDATION, Language.ENG));
    assertThat(text.act()).isEqualTo(new ActIdentifier("32015R1998"));
    assertThat(text.kind()).isEqualTo(Expression.Kind.CONSOLIDATED);
    assertThat(text.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(text.converter()).isEqualTo(conversion.converterVersion());
    assertThat(text.body()).isNotEmpty();
    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  /**
   * The package's files read from the source give what the converter gives from them on disk (its
   * expected output): 2024/2867's descriptor, act, annex and table of contents.
   */
  @ParameterizedTest
  @ValueSource(strings = {CONSOLIDATION, "32024R2867"})
  void givesWhatTheConverterGivesFromTheFilesOnDisk(String document) throws SaxonApiException {
    var xml = conversion.convert(new ExpressionIdentifier(document, Language.ENG)).orElseThrow();

    var serializer = SAXON.newSerializer();
    serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
    serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");
    assertThat(serializer.serializeNodeToString(xml) + "\n")
        .isEqualTo(SampleLegalTexts.text("06.02.1/" + document));
  }

  @Test
  void hasNoTextInALanguageWithoutFormex() {
    assertThat(conversion.convert(new ExpressionIdentifier(CONSOLIDATION, Language.GLE))).isEmpty();
  }
}
