// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView.Fold;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView.Lead;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView.Passage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView.Shown;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's consolidations of 22 March and 1 October 2026 compared, as the Changes view shows
 * them: 2026/449's points (c), (e) and (g) of 8.1.2.3 and of 9.1.2.3. The folds' labels on nums as
 * the converter writes them.
 */
class ChangesViewTest {
  static final ChangesView MARCH_TO_OCTOBER =
      ChangesView.of(
          new Comparison(
              ContentHash.of(new byte[32]),
              ContentHash.of(new byte[32]),
              Differ.differences(text("02015R1998-20260322"), text("02015R1998-20261001"))));

  @Test
  void leadsEachUnitsDifferencesOnceWithTheUnitsBetweenThemFolded() {
    assertThat(MARCH_TO_OCTOBER.passages())
        .extracting(ChangesViewTest::passage)
        .containsExactly(
            "lead",
            "Points (a) and (b)",
            "1",
            "Point (d)",
            "2",
            "Point (f)",
            "3",
            "lead",
            "Points (a) and (b)",
            "4",
            "Point (d)",
            "5",
            "Point (f)",
            "6");
    assertThat(MARCH_TO_OCTOBER.count()).isEqualTo("6 differences");
    assertThat(MARCH_TO_OCTOBER.breakdown()).isEqualTo("0 inserted · 0 deleted · 6 amended");
    assertThat(MARCH_TO_OCTOBER.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
  }

  @Test
  void notesHowManyWordsAnAmendmentInsertedAndDeleted() {
    assertThat(MARCH_TO_OCTOBER.passages())
        .filteredOn(Shown.class::isInstance)
        .map(Shown.class::cast)
        .extracting(Shown::note)
        .containsExactly(
            "Amended: 16 words inserted",
            "Amended: 20 words inserted",
            "Amended: 14 words inserted, 7 deleted",
            "Amended: 16 words inserted",
            "Amended: 20 words inserted",
            "Amended: 14 words inserted, 7 deleted");
  }

  @Test
  void asksForAFoldsUnitsByTheirEIds() {
    var fold = (Fold) MARCH_TO_OCTOBER.passages().get(1);
    assertThat(fold.query())
        .isEqualTo("eId=anx%2Fpnt_8.1.2.3%2Fpnt_a&eId=anx%2Fpnt_8.1.2.3%2Fpnt_b");
  }

  @Test
  void foldsTheDifferencesAnActFilterLeavesOutWithTheUnitsAroundThem() {
    var only9 =
        ChangesView.of(
            new Comparison(
                ContentHash.of(new byte[32]),
                ContentHash.of(new byte[32]),
                Differ.differences(text("02015R1998-20260322"), text("02015R1998-20261001"))),
            difference -> difference.eId().startsWith("anx/pnt_9."),
            "not changed by 2026/449");

    assertThat(only9.passages())
        .extracting(ChangesViewTest::passage)
        .containsExactly(
            "Points 8.1.2.3(a) to 8.1.2.3(g) (7 provisions)",
            "lead",
            "Points (a) and (b)",
            "1",
            "Point (d)",
            "2",
            "Point (f)",
            "3");
    assertThat(only9.passages())
        .filteredOn(Fold.class::isInstance)
        .map(Fold.class::cast)
        .extracting(Fold::note, Fold::same)
        .containsExactly(
            tuple("not changed by 2026/449", false),
            tuple("no differences", true),
            tuple("no differences", true),
            tuple("no differences", true));
    var others = (Fold) only9.passages().getFirst();
    assertThat(others.units()).first().extracting(Sibling::eId).isEqualTo("anx/pnt_8.1.2.3/pnt_a");
    assertThat(others.units()).last().extracting(Sibling::eId).isEqualTo("anx/pnt_8.1.2.3/pnt_g");
    assertThat(only9.count()).isEqualTo("3 differences");
    assertThat(only9.numbers()).containsExactly(1, 2, 3);
  }

  @Test
  void namesAFoldByItsUnitsNums() {
    assertThat(ChangesView.fold(siblings("article", "Article 2", "Article 3", "Article 4")))
        .isEqualTo("Articles 2 to 4");
    assertThat(ChangesView.fold(siblings("annex", "ANNEX I", "ANNEX II")))
        .isEqualTo("ANNEXES I and II");
    assertThat(ChangesView.fold(siblings("division", "CHAPTER II"))).isEqualTo("CHAPTER II");
    assertThat(ChangesView.fold(siblings("paragraph", "2.", "3.")))
        .isEqualTo("Paragraphs 2. and 3.");
    assertThat(ChangesView.fold(siblings("recital", "(1)", "(2)", "(3)")))
        .isEqualTo("Recitals (1) to (3)");
    assertThat(ChangesView.fold(siblings("indent", "—", "—"))).isEqualTo("2 indents");
    assertThat(ChangesView.fold(siblings("subparagraph", ""))).isEqualTo("1 subparagraph");
  }

  @Test
  void namesAFoldAcrossUnitsByItsFirstAndLastNumsCitingAPointsWithItsPoint() {
    assertThat(
            ChangesView.span(
                List.of(
                    new Sibling("anx/pnt_11.1.3", "point", "11.1.3"),
                    new Sibling("anx/pnt_11.2.7", "point", "11.2.7"),
                    new Sibling("anx/pnt_11.2.7/pnt_a", "point", "(a)"),
                    new Sibling("anx/pnt_11.2.7/pnt_b", "point", "(b)"))))
        .isEqualTo("Points 11.1.3 to 11.2.7(b) (4 provisions)");
    assertThat(ChangesView.span(siblings("indent", "—", "—"))).isEqualTo("2 provisions");
  }

  private static String passage(Passage passage) {
    return switch (passage) {
      case Lead lead -> "lead";
      case Fold fold -> fold.label();
      case Shown shown -> String.valueOf(shown.number());
    };
  }

  private static List<Sibling> siblings(String kind, String... nums) {
    return Arrays.stream(nums).map(num -> new Sibling(kind + "_" + num, kind, num)).toList();
  }

  private static Element text(String celex) {
    return Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
  }
}
