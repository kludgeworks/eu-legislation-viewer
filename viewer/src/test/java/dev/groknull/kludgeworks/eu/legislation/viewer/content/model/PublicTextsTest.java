// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Deleted;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Quotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Unit;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests that {@link PublicTexts} gives each text in the public model: valid against its JSON
 * Schema, with every unit of the stored text, and with running text in its two forms.
 */
class PublicTextsTest {
  private static final String NS = "xmlns=\"" + LegalText.NAMESPACE + "\"";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Schema SCHEMA = schema();

  @ParameterizedTest
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts#samples")
  void givesEachSampleValidAgainstTheSchema(String sample) {
    var text = PublicTexts.of(LegalTexts.element(LegalTexts.sample(sample)));

    assertThat(SCHEMA.validate(JSON.valueToTree(text))).extracting(Error::toString).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts#samples")
  void keepsEveryUnitOfEachSample(String sample) {
    var stored = LegalTexts.element(LegalTexts.sample(sample));

    var text = PublicTexts.of(stored);

    var eIds = new ArrayList<String>();
    Stream.of(text.preamble(), text.body(), text.conclusions(), text.annexes())
        .forEach(part -> units(part, eIds));
    text.notes().forEach(note -> units(note.content(), eIds));
    assertThat(eIds).containsExactlyInAnyOrderElementsOf(units(stored, new ArrayList<>()));
  }

  @Test
  void givesRunningTextAsPlainTextAndAsHtml() {
    var text =
        of(
            """
            <body><p>
              <t v="On "/><date date="2026-03-22"><t v="22 March 2026"/></date><t v=", "/>
              <emphasis kind="i"><t v="all"/></emphasis><t v=" "/>
              <emphasis kind="uc"><t v="persons"/></emphasis><noteRef href="#note_1" marker="1"/>
              <t v=" &lt;"/><deleted amendingAct="32019R0103" action="deleted"><t v="old"/></deleted>
              <quote startQuote="‘" endQuote="’"><t v="x"/></quote><eol/><t v="end"/>
            </p></body>
            <notes><note eId="note_1" marker="1"><p><t v="A note."/></p></note></notes>
            """);

    assertThat(text.body())
        .singleElement()
        .isEqualTo(
            new Text(
                "On 22 March 2026, all persons <‘x’\nend",
                "On <time datetime=\"2026-03-22\">22 March 2026</time>, <i>all</i> <span"
                    + " class=\"uc\">persons</span><sup><a href=\"#note_1\">1</a></sup> &lt;<s"
                    + " data-act=\"32019R0103\" data-action=\"deleted\">old</s>‘x’<br>end",
                List.of()));
    assertThat(text.notes()).singleElement().extracting(PublicText.Note::eId).isEqualTo("note_1");
  }

  @Test
  void givesTheLinesOfATitleWithLineBreaks() {
    var text =
        of(
            """
            <preface><longTitle>
              <p><docType><emphasis kind="uc"><t v="Regulation"/></emphasis></docType></p>
              <p><t v="of "/><docDate date="2015-11-05"><t v="5 November 2015"/></docDate></p>
            </longTitle></preface>
            """);

    assertThat(text.title())
        .isEqualTo(
            new PublicText.Inline(
                "Regulation\nof 5 November 2015",
                "<span class=\"uc\">Regulation</span><br>of <time"
                    + " datetime=\"2015-11-05\">5 November 2015</time>"));
  }

  @Test
  void givesAGroupOfRowsAsAHeadingRowBeforeItsRows() {
    var text =
        of(
            """
            <body><table>
              <rowGroup columns="2"><num><t v="A."/></num><heading><t v="Fees"/></heading>
                <tr><td><p><t v="1"/></p></td><td colspan="1"><p><t v="2"/></p></td></tr>
              </rowGroup>
            </table></body>
            """);

    var table = (Table) text.body().getFirst();
    assertThat(table.rows()).hasSize(2);
    assertThat(table.rows().getFirst().header()).isTrue();
    assertThat(table.rows().getFirst().cells())
        .singleElement()
        .satisfies(
            cell -> {
              assertThat(cell.colspan()).isEqualTo(2);
              assertThat(cell.content()).containsExactly(new Text("A. Fees", "A. Fees", List.of()));
            });
    assertThat(table.rows().get(1).cells()).allSatisfy(cell -> assertThat(cell.colspan()).isNull());
  }

  @Test
  void givesAUnitWithTheNotesThatItRefersTo() {
    var stored =
        LegalTexts.element(
            LegalTexts.parse(
                """
                <legalText %s><body>
                  <unit eId="art_1" kind="article"><num><t v="Article 1"/></num>
                    <p><t v="See"/><noteRef href="#note_2" marker="2"/></p></unit>
                  <unit eId="art_2" kind="article"><p><t v="Other"/></p></unit>
                </body><notes>
                  <note eId="note_1" marker="1"><p><t v="One."/></p></note>
                  <note eId="note_2" marker="2"><p><t v="Two."/></p></note>
                </notes></legalText>
                """
                    .formatted(NS)));

    var extract = PublicTexts.extract(stored, "art_1").orElseThrow();

    assertThat(extract.unit().num()).isEqualTo("Article 1");
    assertThat(extract.notes()).extracting(PublicText.Note::eId).containsExactly("note_2");
    assertThat(PublicTexts.extract(stored, "art_9")).isEmpty();
  }

  @Test
  void marksWhatAComparisonChangedAndGivesTheTextAfterIt() {
    var unit =
        PublicTexts.unit(
            element(
                "unit",
                Map.of("eId", "art_1", "kind", "article"),
                element(
                    "p",
                    Map.of(),
                    t("Fees of "),
                    element("diff:del", Map.of(), t("EUR 10")),
                    element("diff:ins", Map.of(), t("EUR 12")))));

    assertThat(unit.content())
        .containsExactly(
            new Text("Fees of EUR 12", "Fees of <del>EUR 10</del><ins>EUR 12</ins>", List.of()));
  }

  private static Element element(String name, Map<String, String> attributes, Element... children) {
    return new Element(name, attributes, List.of(children), "");
  }

  private static Element t(String text) {
    return element("t", Map.of("v", text));
  }

  private static PublicText of(String parts) {
    return PublicTexts.of(
        LegalTexts.element(LegalTexts.parse("<legalText %s>%s</legalText>".formatted(NS, parts))));
  }

  /** Adds the eIds of the units in the blocks, wherever they are. */
  private static void units(List<Block> blocks, List<String> eIds) {
    for (var block : blocks) {
      switch (block) {
        case Unit unit -> {
          eIds.add(unit.eId());
          units(unit.content(), eIds);
        }
        case Table table -> {
          units(table.caption(), eIds);
          table.rows().forEach(row -> row.cells().forEach(cell -> units(cell.content(), eIds)));
          table.notes().forEach(note -> units(note.content(), eIds));
        }
        case Figure figure -> {
          units(figure.caption(), eIds);
          units(figure.description(), eIds);
        }
        case Quotation quotation -> units(quotation.content(), eIds);
        case Deleted deleted -> units(deleted.content(), eIds);
        case Text _ -> {
          // Running text holds no units
        }
      }
    }
  }

  /** Adds the eIds of the units of a stored text, wherever they are. */
  private static List<String> units(Element element, List<String> eIds) {
    if (element.name().equals("unit")) {
      eIds.add(Objects.requireNonNull(element.attribute("eId")));
    }
    element.children().forEach(child -> units(child, eIds));
    return eIds;
  }

  private static Schema schema() {
    try (InputStream in =
        PublicTextsTest.class.getResourceAsStream("/static/api/schema/text.json")) {
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(Objects.requireNonNull(in, "static/api/schema/text.json"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
