// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests the comparison of the consolidations of 2015/1998 of 22 March and 1 October 2026 on the
 * outline of the October text, made by {@link OutlineView}. The comparison has the six amendments
 * by 2026/449: the points (c), (e) and (g) of 8.1.2.3 and of 9.1.2.3. Their sections are 8.1.2 and
 * 9.1.2, both with the heading Screening. A section is the unit with a rank that a point is in.
 */
class OutlineViewTest {
  static final OutlineView MARCH_TO_OCTOBER = outline("02015R1998-20260322", "02015R1998-20261001");

  @Test
  void countsTheDifferencesOnEachBranchDownToTheUnitTheyAreIn() {
    assertThat(counted(MARCH_TO_OCTOBER.entries()))
        .containsExactly(
            "ANNEX 6",
            "8. IN-FLIGHT SUPPLIES 3",
            "8.1 SECURITY CONTROLS 3",
            "8.1.2 Screening 3",
            "9. AIRPORT SUPPLIES 3",
            "9.1 SECURITY CONTROLS 3",
            "9.1.2 Screening 3");
  }

  @Test
  void barsABranchesShareOfTheDifferencesAndJumpsToItsFirst() {
    var annex = find(MARCH_TO_OCTOBER.entries(), "ANNEX");
    assertThat(annex.bar()).isEqualTo(100);
    assertThat(annex.href()).isEqualTo("#difference-1");
    var airport = find(MARCH_TO_OCTOBER.entries(), "9. AIRPORT SUPPLIES");
    assertThat(airport.bar()).isEqualTo(50);
    assertThat(airport.href()).isEqualTo("#difference-4");
    assertThat(airport.barStyle()).isEqualTo("width: 50%");
  }

  @Test
  void listsTheDifferencesInAUnitsPointsAsRowsByTheirOwnNum() {
    assertThat(find(MARCH_TO_OCTOBER.entries(), "8.1.2 Screening").rows())
        .extracting(Row::label, Row::number)
        .containsExactly(tuple("(c)", 1), tuple("(e)", 2), tuple("(g)", 3));
    assertThat(find(MARCH_TO_OCTOBER.entries(), "9.1.2 Screening").rows())
        .extracting(Row::number)
        .containsExactly(4, 5, 6);
    assertThat(find(MARCH_TO_OCTOBER.entries(), "8.1 SECURITY CONTROLS").rows()).isEmpty();
  }

  @Test
  void listsNoRowsUnderAUnitWithMoreThanTwelve() {
    var twelve = IntStream.rangeClosed(1, 12).mapToObj(n -> new Row("(" + n + ")", n)).toList();
    assertThat(OutlineView.listed(twelve)).hasSize(12);
    var thirteen = new ArrayList<>(twelve);
    thirteen.add(new Row("(13)", 13));
    assertThat(OutlineView.listed(thirteen)).isEmpty();
  }

  @Test
  void leavesAUnitWithoutDifferencesUncountedAndWithoutAJump() {
    var general = find(MARCH_TO_OCTOBER.entries(), "8.1.1 Security controls — general provisions");
    assertThat(general.count()).isZero();
    assertThat(general.href()).isNull();
  }

  /** The label and count of each branch with differences, depth first. */
  private static List<String> counted(List<Branch> branches) {
    return all(branches)
        .filter(branch -> branch.count() > 0)
        .map(branch -> branch.label() + " " + branch.count())
        .toList();
  }

  private static Branch find(List<Branch> branches, String label) {
    return all(branches).filter(branch -> branch.label().equals(label)).findFirst().orElseThrow();
  }

  private static Stream<Branch> all(List<Branch> branches) {
    return branches.stream()
        .flatMap(branch -> Stream.concat(Stream.of(branch), all(branch.entries())));
  }

  private static OutlineView outline(String from, String to) {
    var later = Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + to + ".xml")));
    var earlier = Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + from + ".xml")));
    return OutlineView.of(Outlines.of(later), Differ.differences(earlier, later).differences());
  }

  @Test
  void shortensAPartsLabelForTheRail() {
    assertThat(OutlineView.shortened("ANNEX IV")).isEqualTo("IV");
    assertThat(OutlineView.shortened("Article 3")).isEqualTo("Art 3");
    assertThat(OutlineView.shortened("1. AIRPORT SECURITY")).isEqualTo("1.");
    assertThat(OutlineView.shortened("ANNEX")).isEqualTo("Anx");
    assertThat(OutlineView.shortened("PREAMBLE")).isEqualTo("PREA.");
  }
}
