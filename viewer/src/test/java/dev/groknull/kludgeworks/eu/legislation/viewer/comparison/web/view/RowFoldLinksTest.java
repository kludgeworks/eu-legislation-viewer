// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests how {@link RowFoldLinks} gives each fold of rows the address of its rows. */
class RowFoldLinksTest {
  private static final String DIFFERENCE = "/eli/reg/2015/35/eng/compare/difference?eId=anx_I";

  @Test
  void givesAFoldTheAddressOfItsTableAndRows() {
    var unit = new Element("unit", Map.of(), List.of(table(), table()), "");

    assertThat(folds(RowFoldLinks.of(unit, DIFFERENCE)))
        .extracting(fold -> fold.attribute(RowFoldLinks.HREF))
        .containsExactly(DIFFERENCE + "&table=0&rows=0-8", DIFFERENCE + "&table=1&rows=0-8");
  }

  @Test
  void givesTheSameAddressesToAUnitKeptFolded() {
    var unit = new Element("unit", Map.of(), List.of(table(), table()), "");

    assertThat(RowFoldLinks.of(RowFolds.of(unit), DIFFERENCE))
        .isEqualTo(RowFoldLinks.of(unit, DIFFERENCE));
  }

  /** Returns a table of rows r0 to r9, the same in both texts, then r10 with a word inserted. */
  private static Element table() {
    var rows = new ArrayList<Element>();
    for (var i = 0; i <= 10; i++) {
      var words = new ArrayList<Element>();
      words.add(new Element("t", Map.of("v", "r" + i), List.of(), ""));
      if (i == 10) {
        words.add(
            new Element(
                "diff:ins",
                Map.of(),
                List.of(new Element("t", Map.of("v", "new"), List.of(), "")),
                ""));
      }
      var cell = new Element("td", Map.of(), List.of(new Element("p", Map.of(), words, "")), "");
      rows.add(new Element("tr", Map.of(), List.of(cell), ""));
    }
    return new Element("table", Map.of(), rows, "");
  }

  private static List<Element> folds(Element element) {
    var folds = new ArrayList<Element>();
    if (element.name().equals(RowFolds.FOLD)) {
      folds.add(element);
    }
    element.children().forEach(child -> folds.addAll(folds(child)));
    return folds;
  }
}
