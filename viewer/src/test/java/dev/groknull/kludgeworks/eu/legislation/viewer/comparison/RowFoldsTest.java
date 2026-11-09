// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link RowFolds} folds the rows of a changed table that are the same in both texts. In
 * the tables here, a row is named by its text, and a marked row holds a word marked inserted.
 */
class RowFoldsTest {
  @Test
  void foldsTheRowsAwayFromAChangeAndKeepsOneRowOfContextOnEachSide() {
    var table = table(rows("r", 0, 9), marked("r10"), rows("r", 11, 20));

    assertThat(shown(RowFolds.of(table)))
        .containsExactly("9 rows from 0 to 8", "r9", "r10", "r11", "9 rows from 12 to 20");
  }

  @Test
  void showsARunTooShortToFoldAsItIs() {
    var table = table(marked("r0"), rows("r", 1, 4), marked("r5"));

    assertThat(shown(RowFolds.of(table))).containsExactly("r0", "r1", "r2", "r3", "r4", "r5");
  }

  @Test
  void keepsTheHeaderRows() {
    var header = new Element("tr", Map.of("header", "true"), List.of(cell("head", Map.of())), "");
    var table = table(List.of(header), rows("r", 0, 9), marked("r10"));

    assertThat(shown(RowFolds.of(table)))
        .containsExactly("head", "r0", "8 rows from 2 to 9", "r9", "r10");
  }

  @Test
  void foldsTheRowsThatACellSpansTogether() {
    // r7's cell spans r7 and r8, so r8 is context with it
    var spanning = new Element("tr", Map.of(), List.of(cell("r7", Map.of("rowspan", "2"))), "");
    var table = table(rows("r", 0, 6), List.of(spanning), rows("r", 8, 8), marked("r9"));

    assertThat(shown(RowFolds.of(table))).containsExactly("7 rows from 0 to 6", "r7", "r8", "r9");
  }

  @Test
  void foldsEachRowGroupOnItsOwn() {
    var first = new Element("rowGroup", Map.of(), flat(rows("a", 0, 4)), "");
    var second = new Element("rowGroup", Map.of(), flat(rows("b", 0, 3), marked("b4")), "");
    var table = new Element("table", Map.of(), List.of(first, second), "");

    assertThat(shown(RowFolds.of(table)))
        .containsExactly("5 rows from 0 to 4", "3 rows from 5 to 7", "b3", "b4");
  }

  @Test
  void namesTheTableAndTheRowsOfAFold() {
    var unit =
        new Element(
            "unit",
            Map.of(),
            List.of(table(marked("x0")), table(rows("r", 0, 9), marked("r10"))),
            "");

    var fold = folds(RowFolds.of(unit)).getFirst();

    assertThat(fold.attributes())
        .containsEntry(RowFolds.TABLE, "1")
        .containsEntry(RowFolds.FIRST, "0")
        .containsEntry(RowFolds.LAST, "8")
        .containsEntry("columns", "1");
    assertThat(RowFolds.rows(unit, 1, 0, 8))
        .extracting(RowFoldsTest::text)
        .containsExactly("r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8");
  }

  @Test
  void numbersTheTablesWithATableInACell() {
    var inner = table(rows("i", 0, 9));
    var holder =
        new Element("tr", Map.of(), List.of(new Element("td", Map.of(), List.of(inner), "")), "");
    var unit =
        new Element(
            "unit",
            Map.of(),
            List.of(table(List.of(holder), marked("x0")), table(rows("r", 0, 9), marked("r10"))),
            "");

    var fold = folds(RowFolds.of(unit)).getFirst();

    assertThat(fold.attribute(RowFolds.TABLE)).isEqualTo("1");
    assertThat(RowFolds.rows(unit, 1, 0, 0)).extracting(RowFoldsTest::text).containsExactly("r0");
  }

  @Test
  void leavesATableThatHasFoldsAsItIs() {
    var unit =
        new Element(
            "unit",
            Map.of(),
            List.of(table(rows("a", 0, 9), marked("a10")), table(marked("b0"), rows("b", 1, 9))),
            "");

    var folded = RowFolds.of(unit);

    assertThat(RowFolds.of(folded)).isEqualTo(folded);
    assertThat(shown(folded))
        .containsExactly("9 rows from 0 to 8", "a9", "a10", "b0", "b1", "8 rows from 2 to 9");
  }

  @Test
  void givesNoRowsThatTheTableDoesNotHave() {
    var table = table(rows("r", 0, 3));

    assertThat(RowFolds.rows(table, 0, 2, 9)).isEmpty();
    assertThat(RowFolds.rows(table, 1, 0, 1)).isEmpty();
  }

  @SafeVarargs
  private static Element table(List<Element>... rows) {
    return new Element("table", Map.of(), flat(rows), "");
  }

  @SafeVarargs
  private static List<Element> flat(List<Element>... rows) {
    var all = new ArrayList<Element>();
    for (var each : rows) {
      all.addAll(each);
    }
    return all;
  }

  /**
   * Returns the rows {@code name}{@code from} to {@code name}{@code to}, the same in both texts.
   */
  private static List<Element> rows(String name, int from, int to) {
    var rows = new ArrayList<Element>();
    for (var i = from; i <= to; i++) {
      rows.add(new Element("tr", Map.of(), List.of(cell(name + i, Map.of())), ""));
    }
    return rows;
  }

  /** Returns a row named {@code name} with a word marked inserted. */
  private static List<Element> marked(String name) {
    var inserted =
        new Element(
            "diff:ins", Map.of(), List.of(new Element("t", Map.of("v", "new"), List.of(), "")), "");
    var p =
        new Element(
            "p",
            Map.of(),
            List.of(new Element("t", Map.of("v", name), List.of(), ""), inserted),
            "");
    return List.of(
        new Element("tr", Map.of(), List.of(new Element("td", Map.of(), List.of(p), "")), ""));
  }

  private static Element cell(String text, Map<String, String> attributes) {
    var p =
        new Element("p", Map.of(), List.of(new Element("t", Map.of("v", text), List.of(), "")), "");
    return new Element("td", attributes, List.of(p), "");
  }

  /** Returns each row as it shows: its text, or a fold as its count and rows. */
  private static List<String> shown(Element element) {
    var shown = new ArrayList<String>();
    collect(element, shown);
    return shown;
  }

  private static void collect(Element element, List<String> shown) {
    for (var child : element.children()) {
      if (child.name().equals("tr")) {
        shown.add(text(child));
      } else if (child.name().equals(RowFolds.FOLD)) {
        shown.add(
            child.attribute("rows")
                + " rows from "
                + child.attribute(RowFolds.FIRST)
                + " to "
                + child.attribute(RowFolds.LAST));
      } else {
        collect(child, shown);
      }
    }
  }

  private static List<Element> folds(Element element) {
    var folds = new ArrayList<Element>();
    if (element.name().equals(RowFolds.FOLD)) {
      folds.add(element);
    }
    element.children().forEach(child -> folds.addAll(folds(child)));
    return folds;
  }

  /** Returns the first word of {@code row}: its name. */
  private static @Nullable String text(Element row) {
    if (row.name().equals("t")) {
      return row.attribute("v");
    }
    for (var child : row.children()) {
      var text = text(child);
      if (text != null) {
        return text;
      }
    }
    return null;
  }
}
