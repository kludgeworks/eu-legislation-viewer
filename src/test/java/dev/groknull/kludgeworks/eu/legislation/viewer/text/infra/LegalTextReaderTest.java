// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Changed;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DateRef;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.DeletedBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.ListBlock;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Paragraph;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Provenance;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Unit;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link LegalTextReader} on the consolidated 2015/1998 of 22 March 2026 (a {@link SampleLegalTexts
 * sample}), the excerpts of the worked example (Notion: Worked example: 2015/1998 source to
 * target), which XSpec checks on the XML side.
 */
class LegalTextReaderTest {

  /** Formex dates keep a non-breaking space after the day. */
  static final char NBSP = '\u00a0';

  static final Processor SAXON = new Processor(false);
  static final LegalTextReader READER = new LegalTextReader(SAXON);

  static final String CONSOLIDATED = "06.02.1/02015R1998-20260322";

  static LegalText consolidated;

  @BeforeAll
  static void read() {
    consolidated = READER.read(SampleLegalTexts.read(SAXON, CONSOLIDATED));
  }

  @Test
  void readsTheHeader() {
    assertThat(consolidated.act()).isEqualTo("32015R1998");
    assertThat(consolidated.kind()).isEqualTo(LegalText.Kind.CONSOLIDATED);
    assertThat(consolidated.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(consolidated.formexVersion()).isEqualTo("06.02.1");
    assertThat(consolidated.source().celex()).isEqualTo("02015R1998-20260322");
    var lifecycle = consolidated.lifecycle();
    assertThat(lifecycle).isNotNull();
    assertThat(lifecycle.events())
        .last()
        .satisfies(
            e -> {
              assertThat(e.act()).isEqualTo("32026R0449");
              assertThat(e.date()).isEqualTo(LocalDate.of(2026, 2, 27));
            });
  }

  /** Point 12.6.2: three amending acts, on the unit, on a paragraph and on a date inside it. */
  @Test
  void readsProvenanceAtEveryLevel() {
    var point = unit("anx/pnt_12.6.2");
    assertThat(point.kind()).isEqualTo(Unit.Kind.LEVEL);
    assertThat(point.depth()).isEqualTo(4);
    assertThat(point.provenance())
        .containsExactly(
            new Provenance(
                "32023R0566",
                Provenance.Action.REPLACED,
                "AR:1 .AND. AX:0;PT:19",
                "O018001M025000"));
    var second = (Paragraph) point.content().get(1);
    assertThat(second.provenance())
        .extracting(Provenance::amendingAct)
        .containsExactly("32024R1255");
    var changed =
        second.content().stream()
            .filter(Changed.class::isInstance)
            .map(Changed.class::cast)
            .findFirst();
    assertThat(changed)
        .hasValueSatisfying(
            c -> {
              assertThat(c.provenance().amendingAct()).isEqualTo("32026R0449");
              assertThat(c.content())
                  .singleElement()
                  .isEqualTo(
                      new DateRef(LocalDate.of(2026, 12, 31), "31" + NBSP + "December 2026"));
            });
  }

  /** Point 11.1.1: a list of four points, then a paragraph 2026/449 deleted. */
  @Test
  void keepsDeletedTextApart() {
    var point = unit("anx/pnt_11.1.1");
    assertThat(point.content()).hasSize(3);
    assertThat(point.content().get(1))
        .isInstanceOfSatisfying(
            ListBlock.class,
            list -> {
              assertThat(list.kind()).isEqualTo(ListBlock.Kind.ALPHA);
              assertThat(list.items())
                  .extracting(item -> ((Unit) item).eId().value())
                  .containsExactly(
                      "anx/pnt_11.1.1/pnt_a",
                      "anx/pnt_11.1.1/pnt_b",
                      "anx/pnt_11.1.1/pnt_c",
                      "anx/pnt_11.1.1/pnt_d");
            });
    assertThat(point.content().get(2))
        .isInstanceOfSatisfying(
            DeletedBlock.class,
            deleted ->
                assertThat(deleted.provenance())
                    .extracting(Provenance::amendingAct, Provenance::action)
                    .containsExactly(tuple("32026R0449", Provenance.Action.DELETED)));
  }

  @Test
  void readsTextExactly() {
    var first = (Paragraph) unit("anx/pnt_1.0.2").content().getFirst();
    assertThat(first.content())
        .containsExactly(
            new Text(
                "For the purposes of this Chapter, an aircraft, bus, baggage cart or other means of"
                    + " transport, or a walkway or jetway, shall be regarded as a part of an"
                    + " airport."));
  }

  @Test
  void hasImmutableTrees() {
    var annexes = consolidated.annexes();
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> annexes.add(annexes.getFirst()));
  }

  /** A value the schema doesn't know, such as a unit kind from a newer converter, fails loudly. */
  @Test
  void refusesAValueTheSchemaDoesNotKnow() throws SaxonApiException {
    var xml =
        SAXON
            .newSerializer()
            .serializeNodeToString(SampleLegalTexts.read(SAXON, CONSOLIDATED))
            .replaceFirst("kind=\"level\"", "kind=\"clause\"");
    var newer = SAXON.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
    assertThatExceptionOfType(InvalidLegalTextException.class)
        .isThrownBy(() -> READER.read(newer))
        .withMessageContaining("clause");
  }

  @Test
  void refusesWhatIsNotLegalText() throws SaxonApiException {
    var notLegalText =
        SAXON.newDocumentBuilder().build(new StreamSource(new StringReader("<legalText/>")));
    assertThatExceptionOfType(InvalidLegalTextException.class)
        .isThrownBy(() -> READER.read(notLegalText));
  }

  private static Unit unit(String eId) {
    return units(consolidated.annexes().stream())
        .filter(u -> u.eId().value().equals(eId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no unit " + eId));
  }

  private static Stream<Unit> units(Stream<Block> blocks) {
    return blocks
        .filter(Unit.class::isInstance)
        .map(Unit.class::cast)
        .flatMap(u -> Stream.concat(Stream.of(u), units(u.content().stream())));
  }
}
