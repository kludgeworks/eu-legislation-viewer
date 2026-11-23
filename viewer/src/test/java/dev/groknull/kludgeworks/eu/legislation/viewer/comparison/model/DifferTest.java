// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.JANUARY;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.MARCH;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.OCTOBER;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.block;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.descendants;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.formula;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.occurrence;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.only;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.row;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.said;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.table;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Differ} from end to end: a text against itself, a preamble that only one text has,
 * and the comparison of the consolidations of 2015/1998 of 22 March and 1 October 2026. 2026/449
 * applies from 22 March. Its points on screening of in-flight supplies and airport supplies apply
 * from 1 October.
 *
 * <p>Each stage has its own tests: {@code PairingTest}, {@code ComparerTest}, {@code
 * ComparedOccurrenceTest}, {@code OccurrenceEditsTest} and {@code OrderingTest}. The tests of
 * words, spaces, tables and formulas move to the tests of Edits and Pieces with step 3. The
 * business rules are in viewer/src/test/rules/comparison.
 */
class DifferTest {
  @Test
  void findsNothingBetweenATextAndItself() {
    assertThat(Differ.differences(MARCH, MARCH).differences()).isEmpty();
  }

  @Test
  void leavesOutAPreambleOnlyOneTextHasAndSaysWhichHasNone() {
    var withPreamble = withPreamble(JANUARY);

    var dropped = Differ.differences(withPreamble, JANUARY);
    var added = Differ.differences(JANUARY, withPreamble);
    var both = Differ.differences(withPreamble, withPreamble);

    assertThat(dropped.differences()).isEmpty();
    assertThat(dropped.withoutPreamble()).isEqualTo(Side.LATER);
    assertThat(added.differences()).isEmpty();
    assertThat(added.withoutPreamble()).isEqualTo(Side.EARLIER);
    assertThat(both.withoutPreamble()).isNull();
  }

  @Test
  void marksTheWordsAnActInsertedInEachPointItChanged() {
    var differences = Differ.differences(MARCH, OCTOBER).differences();
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(
            tuple("anx/pnt_8.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_g", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_g", Kind.CHANGED));
    var c = differences.getFirst();
    assertThat(said(only(c.shown(), "p")))
        .isEqualTo(
            "x-ray equipment[+ applied in accordance with Attachment 6-J to the Annex to"
                + " Implementing Decision C(2015) 8005];");
    assertThat(said(only(c.shown(), "num"))).isEqualTo("(c)");
    assertThat(c.inserted()).isEqualTo(16);
    assertThat(c.deleted()).isZero();
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void keepsTheTextsStructureAroundItsEdits() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst().shown();
    assertThat(c.children()).extracting(Element::name).containsExactly("tag", "num", "p");
    assertThat(descendants(c))
        .filteredOn(element -> element.name().startsWith("diff:"))
        .allSatisfy(
            edit -> assertThat(edit.children()).extracting(Element::name).containsOnly("t"));
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void comparesATableRowByRowAndCellByCell() {
    var differences =
        Differ.differences(text("02018R2019-20250220"), text("02018R2019-20250623")).differences();
    // A list inserted in the cell of Alnus renumbers the lists in the cells after it, list_2 to
    // list_3 and so on. Their occurrences are not differences of their own.
    assertThat(differences).extracting(Difference::eId).containsExactly("anx/level_1/pnt_1");
    var alnus =
        descendants(differences.getFirst().shown()).stream()
            .filter(each -> each.name().equals("tr") && said(each).contains("Alnus"))
            .findFirst()
            .orElseThrow();
    var cells = alnus.children().stream().filter(each -> each.name().equals("td")).toList();
    assertThat(said(cells.get(0))).isEqualTo("ex0602");
    assertThat(said(cells.get(1)))
        .startsWith("Alnus Mill.[+, other than:][+up to 2-year-old graftwood of ]");
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void showsARowDeletedFromATableAsARowWhereItStood() {
    var from = text(occurrence("art_1", table(row("A", "1"), row("B", "2"), row("C", "3"))));
    var to = text(occurrence("art_1", table(row("A", "1"), row("C", "3"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows).extracting(BuiltTexts::said).containsExactly("A1", "[-B][-2]", "C3");
    assertThat(difference.deleted()).isEqualTo(2);
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void pairsAChangedRowWithTheRowItWasNotWithARowInsertedBeforeIt() {
    var from =
        text(
            occurrence(
                "art_1",
                table(
                    row("Acacia", "all third countries"),
                    row("Alnus", "all third countries"),
                    row("Betula", "all third countries"))));
    var to =
        text(
            occurrence(
                "art_1",
                table(
                    row("Acacia", "all third countries"),
                    row("Albizia", "Israel"),
                    row("Alnus", "all third countries other than the United Kingdom"),
                    row("Betula", "all third countries"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows)
        .extracting(BuiltTexts::said)
        .containsExactly(
            "Acaciaall third countries",
            "[+Albizia][+Israel]",
            "Alnusall third countries[+ other than the United Kingdom]",
            "Betulaall third countries");
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void comparesAChangedCellWithTheCellAboveIt() {
    var from = text(occurrence("art_1", table(row("A", "1"), row("B", "2"))));
    var to = text(occurrence("art_1", table(row("A", "1"), row("B", "20"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows.get(1).children())
        .extracting(BuiltTexts::said)
        .containsExactly("B", "[-2][+20]");
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void readsAFormBlankInsideAFieldThatOnlyOneTextPrintsAsNoChange() {
    // As point 2.17.1 of Appendix 3 to Annex I to 692/2008, of 1 January 2016.
    var from = text(occurrence("pnt_2.17.1", block("p", "Mass in running order: … kg")));
    var to = text(occurrence("pnt_2.17.1", block("p", "Mass in running order: kg")));
    assertThat(Differ.differences(from, to).differences()).isEmpty();
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void readsAnEllipsisJoinedToAWordAsAChange() {
    var from = text(occurrence("art_1", block("p", "as set out in Annex…")));
    var to = text(occurrence("art_1", block("p", "as set out in Annex")));
    assertThat(Differ.differences(from, to).differences()).hasSize(1);
  }

  // Moves with step 3 (Edits): a decision of Edits and Pieces.
  @Test
  void showsAChangedFormulaWholeAsDeletedThenInserted() {
    var from = text(occurrence("art_1", formula("1")));
    var to = text(occurrence("art_1", formula("2")));
    var difference = Differ.differences(from, to).differences().getFirst();
    var p = only(difference.shown(), "p");
    assertThat(p.children()).extracting(Element::name).containsExactly("t", "diff:del", "diff:ins");
    assertThat(p.children().get(1).children()).extracting(Element::name).containsExactly("math");
    assertThat(p.children().get(2).children()).extracting(Element::name).containsExactly("math");
  }

  /**
   * {@code text} with a preamble of one recital before its content, as in the consolidation of
   * 2016.
   */
  private static Element withPreamble(Element text) {
    var recital =
        new Element(
            "occurrence",
            Map.of("eId", "pbl_1/rct_1", "kind", "recital"),
            List.of(new Element("p", Map.of(), List.of(), "Whereas:")),
            "");
    var preamble = new Element("preamble", Map.of("eId", "pbl_1"), List.of(recital), "");
    var children = new ArrayList<Element>();
    children.add(preamble);
    children.addAll(text.children());
    return new Element(text.name(), text.attributes(), children, "");
  }
}
