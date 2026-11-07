// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Chip;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Outlines;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's consolidations of 22 March and 1 October 2026 compared, on October's outline:
 * 2026/449's six amendments, points (c), (e) and (g) of 8.1.2.3 and of 9.1.2.3, whose sections (the
 * units with a rank they are in) are 8.1.2 and 9.1.2, Screening.
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
  void listsTheDifferencesInAUnitsPointsAsChips() {
    assertThat(find(MARCH_TO_OCTOBER.entries(), "8.1.2 Screening").chips())
        .extracting(Chip::label, Chip::number)
        .containsExactly(tuple("8.1.2.3 (c)", 1), tuple("8.1.2.3 (e)", 2), tuple("8.1.2.3 (g)", 3));
    assertThat(find(MARCH_TO_OCTOBER.entries(), "9.1.2 Screening").chips())
        .extracting(Chip::number)
        .containsExactly(4, 5, 6);
    assertThat(find(MARCH_TO_OCTOBER.entries(), "8.1 SECURITY CONTROLS").chips()).isEmpty();
  }

  @Test
  void leavesAUnitWithoutDifferencesUncountedAndWithoutAJump() {
    var general = find(MARCH_TO_OCTOBER.entries(), "8.1.1 Security controls — general provisions");
    assertThat(general.count()).isZero();
    assertThat(general.href()).isNull();
  }

  /** The branches with differences, depth first: label and count. */
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
    var later = Encoder.encode(LegalTexts.sample("06.02.1/" + to + ".xml"));
    var earlier = Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + from + ".xml")));
    return OutlineView.of(
        Outlines.of(later), Differ.differences(earlier, Decoder.element(later)).differences());
  }
}
