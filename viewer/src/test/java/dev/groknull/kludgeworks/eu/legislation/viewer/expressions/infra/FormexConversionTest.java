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
import org.junit.jupiter.api.Test;

/**
 * A document's legal text converted from its Formex: from the source, by the real converter; here
 * the source is the converter's fixture of 2015/1998's consolidation of 22 March 2026.
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

  @Test
  void deletesTheFormexOnceConverted() {
    conversion.convert(new ExpressionIdentifier(CONSOLIDATION, Language.ENG));

    assertThat(source.folders).isNotEmpty().allSatisfy(folder -> assertThat(folder).doesNotExist());
  }

  @Test
  void hasNoTextInALanguageWithoutFormex() {
    assertThat(conversion.convert(new ExpressionIdentifier(CONSOLIDATION, Language.GLE))).isEmpty();
    assertThat(source.folders).allSatisfy(folder -> assertThat(folder).doesNotExist());
  }
}
