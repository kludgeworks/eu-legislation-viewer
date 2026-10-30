// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.build.Schematron;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
  void letsUnitsWhoseLabelTheSourceRepeatsShareAnEId() throws SaxonApiException {
    var text =
        legalText(
            """
            <body><unit eId="art_1" kind="article" ambiguous="true"/>
                  <unit eId="art_1" kind="article" ambiguous="true"/></body>\
            """);
    assertThat(RULES.violations(text)).isEmpty();
  }

  @Test
  void rejectsASharedEIdNotAllMarkedAmbiguous() throws SaxonApiException {
    var text =
        legalText(
            """
            <body><unit eId="art_1" kind="article" ambiguous="true"/>
                  <unit eId="art_1" kind="article"/></body>\
            """);
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("unique-live-eid", "unique-live-eid");
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

  @Test
  void acceptsAConsolidatedVersionDatedAsItsCelex() throws SaxonApiException {
    var text =
        legalText(
            "kind=\"consolidated\" act=\"32015R1998\" version=\"2026-03-22\"",
            "<source celex=\"02015R1998-20260322\"/><lifecycle/><body/>");
    assertThat(RULES.violations(text)).isEmpty();
  }

  @Test
  void rejectsAVersionDateTheCelexDoesNotHave() throws SaxonApiException {
    var text =
        legalText(
            "kind=\"consolidated\" act=\"32015R1998\" version=\"2026-01-01\"",
            "<source celex=\"02015R1998-20260322\"/><lifecycle/><body/>");
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("version-in-celex");
  }

  @Test
  void rejectsAVersionOnAnOriginalText() throws SaxonApiException {
    var text =
        legalText(
            "kind=\"original\" act=\"32015R1998\" version=\"2015-11-05\"",
            "<source celex=\"32015R1998\"/><body/>");
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("version-of-consolidation");
  }

  @Test
  void acceptsACorrigendumOfTheActItCorrects() throws SaxonApiException {
    var text =
        legalText(
            "kind=\"corrigendum\" act=\"52017XC1006(02)\"",
            "<source celex=\"52017XC1006(02)R(01)\"/><body/>");
    assertThat(RULES.violations(text)).isEmpty();
  }

  @Test
  void rejectsACorrigendumThatIsItsOwnAct() throws SaxonApiException {
    var text =
        legalText(
            "kind=\"corrigendum\" act=\"32009R0631R(01)\"",
            "<source celex=\"32009R0631R(01)\"/><body/>");
    assertThat(RULES.violations(text))
        .extracting(Schematron.Finding::id)
        .containsExactly("act-of-corrigendum");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "levels-only | <body><unit eId=\"art_1\" kind=\"article\" depth=\"2\"/></body>",
        "annexes-only | <body><unit eId=\"art_1\" kind=\"article\" eurlexAnchor=\"1\"/></body>",
        "ref-by-kind | <body><p><ref kind=\"uri\" href=\"http://x\" series=\"L\"><t"
            + " v=\"x\"/></ref></p></body>",
        "correction-sides | <body><unit eId=\"art_1\" kind=\"article\"><old/></unit></body>",
        "deletion-in-deleted | <body><p><provenance amendingAct=\"32026R0449\" action=\"deleted\""
            + " marker=\"O1\"/><t v=\"x\"/></p></body>",
        "note-marker | <body><p><noteRef href=\"#note_1\" marker=\"2\"/></p></body><notes><note"
            + " eId=\"note_1\" marker=\"1\" kind=\"footnote\"/></notes>"
      })
  void rejectsWhatItsKindDoesNotHold(String rule, String content) throws SaxonApiException {
    assertThat(RULES.violations(legalText(content)))
        .extracting(Schematron.Finding::id)
        .containsExactly(rule);
  }

  private static XdmNode legalText(String content) throws SaxonApiException {
    return legalText("", content);
  }

  private static XdmNode legalText(String attributes, String content) throws SaxonApiException {
    var xml =
        "<legalText xmlns=\"https://kludgeworks.groknull.dev/ns/legal-text/1\" "
            + attributes
            + ">"
            + content
            + "</legalText>";
    return SAXON.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
  }
}
