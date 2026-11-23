// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.descendants;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.row;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.said;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.table;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Edits} on tables, piece by piece. The rule is table-row-by-row
 * (how-two-texts-pair.feature), whose example has rows inserted and deleted whole. These tests
 * cover what it does not: the edits in one cell, a row deleted where it stood, and a changed row
 * with a row inserted before it.
 */
class EditsTest {

  @Test
  void comparesATableRowByRowAndCellByCell() {
    // As 2018/2019 of 23 June 2025: a list inserted in the cell of Alnus.
    var edited =
        Edits.between(
                tableOf(text("02018R2019-20250220"), "anx/level_1/pnt_1"),
                tableOf(text("02018R2019-20250623"), "anx/level_1/pnt_1"))
            .element();
    var alnus =
        descendants(edited).stream()
            .filter(each -> each.name().equals("tr") && said(each).contains("Alnus"))
            .findFirst()
            .orElseThrow();
    var cells = alnus.children().stream().filter(each -> each.name().equals("td")).toList();
    assertThat(said(cells.get(0))).isEqualTo("ex0602");
    assertThat(said(cells.get(1)))
        .startsWith("Alnus Mill.[+, other than:][+up to 2-year-old graftwood of ]");
  }

  @Test
  void showsARowDeletedFromATableAsARowWhereItStood() {
    var edited =
        Edits.between(
            table(row("A", "1"), row("B", "2"), row("C", "3")),
            table(row("A", "1"), row("C", "3")));
    assertThat(edited.element().children())
        .extracting(BuiltTexts::said)
        .containsExactly("A1", "[-B][-2]", "C3");
    assertThat(edited.deleted()).isEqualTo(2);
  }

  @Test
  void pairsAChangedRowWithTheRowItWasNotWithARowInsertedBeforeIt() {
    var edited =
        Edits.between(
            table(
                row("Acacia", "all third countries"),
                row("Alnus", "all third countries"),
                row("Betula", "all third countries")),
            table(
                row("Acacia", "all third countries"),
                row("Albizia", "Israel"),
                row("Alnus", "all third countries other than the United Kingdom"),
                row("Betula", "all third countries")));
    assertThat(edited.element().children())
        .extracting(BuiltTexts::said)
        .containsExactly(
            "Acaciaall third countries",
            "[+Albizia][+Israel]",
            "Alnusall third countries[+ other than the United Kingdom]",
            "Betulaall third countries");
  }

  @Test
  void comparesAChangedCellWithTheCellAboveIt() {
    var edited =
        Edits.between(table(row("A", "1"), row("B", "2")), table(row("A", "1"), row("B", "20")));
    assertThat(edited.element().children().get(1).children())
        .extracting(BuiltTexts::said)
        .containsExactly("B", "[-2][+20]");
  }

  /** The table in the own blocks of the occurrence {@code eId} of {@code text}. */
  private static Element tableOf(Element text, String eId) {
    return Objects.requireNonNull(Occurrences.of(text).get(eId), eId).blocks().stream()
        .flatMap(block -> Stream.concat(Stream.of(block), descendants(block).stream()))
        .filter(each -> each.name().equals("table"))
        .findFirst()
        .orElseThrow();
  }
}
