// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every sample legal text (see {@link SampleLegalTexts}) reads into records that hold every text
 * value and every eId of the XML, in order: the mapping lost nothing.
 */
class LegalTextRecordsTest {

  static final Processor SAXON = new Processor(false);
  static final LegalTextReader READER = new LegalTextReader(SAXON);

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.SampleLegalTexts#names")
  void holdsEverythingTheXmlHolds(String sample) throws SaxonApiException {
    var legalText = SampleLegalTexts.read(SAXON, sample);
    var records = RecordContents.of(READER.read(legalText));
    var xpath = SAXON.newXPathCompiler();
    xpath.declareNamespace("lt", "https://kludgeworks.groknull.dev/ns/legal-text/1");
    assertThat(records.texts)
        .as("%s: text values of the records", sample)
        .containsExactlyElementsOf(strings(xpath.evaluate("//lt:t/@v", legalText)));
    assertThat(records.eIds)
        .as("%s: eIds of the records", sample)
        .containsExactlyElementsOf(strings(xpath.evaluate("//@eId", legalText)));
    assertThat(records.eIds)
        .as("%s: the notes the records refer to", sample)
        .containsAll(records.noteRefs);
  }

  private static List<String> strings(XdmValue values) {
    return values.stream().map(XdmItem::getStringValue).toList();
  }
}
