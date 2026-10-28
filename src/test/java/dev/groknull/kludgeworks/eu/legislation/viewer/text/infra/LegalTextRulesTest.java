// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.build.Schematron;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;

/** What legal-text.sch accepts and rejects. */
class LegalTextRulesTest {

  static final Processor SAXON = new Processor(false);
  static final LegalTextRules RULES = new LegalTextRules(SAXON);

  @Test
  void acceptsUniqueEIdsAndResolvedNoteReferences() throws SaxonApiException {
    var text =
        legalText(
            """
            <body><unit eId="art_1" kind="article"><p><t v="a"/><noteRef href="#note_1" marker="1"/></p></unit>
                  <unit eId="art_2" kind="article"/></body>
            <notes><note eId="note_1" marker="1" kind="footnote"/></notes>\
            """);
    assertThat(RULES.violations(text)).isEmpty();
  }

  @Test
  void rejectsTwoLiveUnitsWithOneEId() throws SaxonApiException {
    var text =
        legalText(
            "<body><unit eId=\"art_1\" kind=\"article\"/><unit eId=\"art_1\""
                + " kind=\"article\"/></body>");
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("unique-live-eid", "unique-live-eid");
  }

  @Test
  void letsAReplacementReuseADeletedPlaceholdersEId() throws SaxonApiException {
    var text =
        legalText(
            """
            <body><deleted><provenance amendingAct="32026R0449" action="deleted" marker="O1"/>
                    <unit eId="art_1" kind="article"/></deleted>
                  <unit eId="art_1" kind="article"/></body>\
            """);
    assertThat(RULES.violations(text)).isEmpty();
  }

  @Test
  void rejectsANoteReferenceToNoNote() throws SaxonApiException {
    var text = legalText("<body><p><noteRef href=\"#note_9\" marker=\"9\"/></p></body>");
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("note-exists");
  }

  @Test
  void rejectsPiecesOfOneMarkerThatDisagree() throws SaxonApiException {
    var text =
        legalText(
            """
            <body><p><changed amendingAct="32026R0449" action="replaced" marker="O1"><t v="a"/></changed></p>
                  <p><changed amendingAct="32019R0103" action="replaced" marker="O1"><t v="b"/></changed></p></body>\
            """);
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .contains("marker-consistent");
  }

  private static XdmNode legalText(String content) throws SaxonApiException {
    var xml =
        "<legalText xmlns=\"https://kludgeworks.groknull.dev/ns/legal-text/1\">"
            + content
            + "</legalText>";
    return SAXON.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
  }
}
