// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests how {@link Positions} names units without a number, as the Joint Practical Guide does. */
class PositionsTest {

  @Test
  void spellsOutTheirPositionsInAFold() {
    assertThat(Positions.spelled(List.of(sub("art_3/par_1/sub_1")), "subparagraph"))
        .contains("First subparagraph");
    assertThat(
            Positions.spelled(
                List.of(sub("art_3/par_1/sub_1"), sub("art_3/par_1/sub_2")), "subparagraph"))
        .contains("First and second subparagraphs");
    assertThat(
            Positions.spelled(
                List.of(
                    new Sibling("anx/pnt_1/idt_2", "indent", "—"),
                    new Sibling("anx/pnt_1/idt_3", "indent", "—"),
                    new Sibling("anx/pnt_1/idt_4", "indent", "—")),
                "indent"))
        .contains("Second to fourth indents");
  }

  @Test
  void leavesUnitsWithoutAPositionOrOfMixedKindsToBeCounted() {
    assertThat(Positions.spelled(List.of(new Sibling("indent_—", "indent", "—")), "indent"))
        .isEmpty();
    assertThat(
            Positions.spelled(
                List.of(sub("art_3/sub_1"), new Sibling("art_3/idt_1", "indent", "")),
                "subparagraph"))
        .isEmpty();
  }

  @Test
  void namesAnOutlineRowShortWithItsArticle() {
    assertThat(Positions.row("art_9/sub_2", "subparagraph")).contains("Art. 9, 2nd subpara.");
    assertThat(Positions.row("art_3/par_1/sub_2", "subparagraph"))
        .contains("Art. 3(1), 2nd subpara.");
    assertThat(Positions.row("art_2/unp_1", "paragraph")).contains("Art. 2, 1st para.");
    assertThat(Positions.row("anx/pnt_1/idt_3", "indent")).contains("3rd indent");
  }

  @Test
  void writesOrdinals() {
    assertThat(Positions.ordinal(2)).isEqualTo("second");
    assertThat(Positions.ordinal(21)).isEqualTo("21st");
    assertThat(Positions.shortOrdinal(12)).isEqualTo("12th");
    assertThat(Positions.shortOrdinal(23)).isEqualTo("23rd");
  }

  private static Sibling sub(String eId) {
    return new Sibling(eId, "subparagraph", "");
  }
}
