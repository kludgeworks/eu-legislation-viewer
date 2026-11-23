// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.annexWithTags;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.block;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.occurrence;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.paragraph;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.pointWithNum;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Pairing}: which occurrence of the later text is which occurrence of the earlier
 * text. The tests cover the shapes that no example of the rules covers
 * (how-two-texts-pair.feature): renumbered occurrences with occurrences in them, words that are
 * only alike, another language, weak evidence, tags left from an earlier renumbering, and moves out
 * of occurrences further up.
 */
class PairingTest {

  @Test
  void leavesSubPointsInsideDeletedPointWhenNextPointIsRenumberedUp() {
    var earlier =
        text(
            pointWithNum("1/pnt_a", "(a)", "Alpha", "A", "replaced"),
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "Bravo",
                "A",
                "replaced",
                pointWithNum("1/pnt_b/sub_1", "(i)", "Item 1", "A", "replaced")),
            pointWithNum("1/pnt_c", "(c)", "Charlie", "A", "replaced"));
    var later =
        text(
            pointWithNum("1/pnt_a", "(a)", "Alpha", "A", "replaced"),
            pointWithNum("1/pnt_b", "(b)", "Charlie", "B", "renumbered"));

    assertThat(was(earlier, later, "1/pnt_b")).hasValue("1/pnt_c");
    assertThat(is(earlier, later, "1/pnt_b")).isEmpty();
    assertThat(is(earlier, later, "1/pnt_b/sub_1")).isEmpty();
  }

  @Test
  void pairsSubPointsOfRenumberedPoint() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "Bravo",
                "A",
                "replaced",
                pointWithNum("1/pnt_b/sub_1", "(i)", "Item 1", "A", "replaced")));
    var later =
        text(
            pointWithNum("1/pnt_a", "(a)", "Alpha", "B", "inserted"),
            pointWithNum(
                "1/pnt_c",
                "(c)",
                "Bravo",
                "B",
                "renumbered",
                pointWithNum("1/pnt_c/sub_1", "(i)", "Item 1", "B", "replaced")));

    assertThat(was(earlier, later, "1/pnt_a")).isEmpty();
    assertThat(was(earlier, later, "1/pnt_c")).hasValue("1/pnt_b");
    assertThat(was(earlier, later, "1/pnt_c/sub_1")).hasValue("1/pnt_b/sub_1");
  }

  @Test
  void pairsRenumberedPointWhoseWordingAlsoChanged() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_a",
                "(a)",
                "screening of passengers shall be carried out by walk-through metal detection",
                "A",
                "replaced"));
    var later =
        text(
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "screening of passengers shall be conducted by walk-through metal detection and"
                    + " explosives detection",
                "B",
                "renumbered"));

    assertThat(was(earlier, later, "1/pnt_b")).hasValue("1/pnt_a");
  }

  @Test
  void pairsRenumberedPointsInNonAsciiLanguageByWordingSimilarity() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_a",
                "(α)",
                "Έλεγχος ασφαλείας επιβατών με ανιχνευτή μετάλλων",
                "A",
                "replaced"));
    var later =
        text(
            pointWithNum("1/pnt_a", "(α)", "Νέο στοιχείο για τους ελέγχους", "B", "inserted"),
            pointWithNum(
                "1/pnt_b",
                "(β)",
                "Έλεγχος ασφαλείας επιβατών με ανιχνευτή μετάλλων και εκρηκτικών",
                "B",
                "renumbered"));

    assertThat(was(earlier, later, "1/pnt_a")).isEmpty();
    assertThat(was(earlier, later, "1/pnt_b")).hasValue("1/pnt_a");
  }

  @Test
  void pairsRenumberedPointCorrectlyWhenDeletedSiblingHasOverlappingWording() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_a",
                "(a)",
                "The competent authority shall maintain records of all inspections.",
                "A",
                "replaced"),
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "The competent authority shall conduct security screening and inspections.",
                "A",
                "replaced"),
            pointWithNum(
                "1/pnt_c",
                "(c)",
                "The competent authority shall verify compliance with safety standards.",
                "A",
                "replaced"));
    var later =
        text(
            pointWithNum(
                "1/pnt_a",
                "(a)",
                "The competent authority shall maintain records of all inspections.",
                "A",
                "replaced"),
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "The competent authority shall verify compliance with safety standards and rules.",
                "B",
                "renumbered"));

    assertThat(was(earlier, later, "1/pnt_b")).hasValue("1/pnt_c");
    assertThat(is(earlier, later, "1/pnt_b")).isEmpty();
  }

  @Test
  void leavesRenumberedPointUnpairedWhenEvidenceIsWeak() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_a",
                "(a)",
                "The competent authority shall maintain records of all inspections.",
                "A",
                "replaced"),
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "Security rules for maritime ports and terminal facilities.",
                "A",
                "replaced"));
    var later =
        text(
            pointWithNum(
                "1/pnt_a",
                "(a)",
                "The competent authority shall maintain records of all inspections.",
                "A",
                "replaced"),
            pointWithNum(
                "1/pnt_b",
                "(b)",
                "Civil aviation operations conducted by licensed commercial air carriers.",
                "B",
                "renumbered"));

    assertThat(was(earlier, later, "1/pnt_b")).isEmpty();
    assertThat(is(earlier, later, "1/pnt_b")).isEmpty();
  }

  @Test
  void pairsOccurrencesInRealHighRiskPlantsFixture() {
    var earlier = text("02018R2019-20250220");
    var later = text("02018R2019-20250623");

    assertThat(was(earlier, later, "anx")).hasValue("anx");
    assertThat(was(earlier, later, "anx/level_1/pnt_1")).hasValue("anx/level_1/pnt_1");
  }

  @Test
  void pairsOccurrencesInRealSanctionsDecisionFixture() {
    var earlier = text("02022D2319-20241216");
    var later = text("02022D2319-20250326");

    assertThat(was(earlier, later, "anx_I")).hasValue("anx_I");
    assertThat(was(earlier, later, "art_1/par_1")).hasValue("art_1/par_1");
  }

  @Test
  void pairsStructureLevelOccurrenceRenumberedViaTags() {
    var earlier =
        text(
            annexWithTags("anx_I", "ANNEX I", "Provisions on security screening.", "A", "replaced"),
            annexWithTags("anx_II", "ANNEX II", "Provisions on chemical safety.", "A", "replaced"));
    var later =
        text(
            annexWithTags("anx_I", "ANNEX I", "Provisions on chemical safety.", "B", "renumbered"));

    assertThat(was(earlier, later, "anx_I")).hasValue("anx_II");
    assertThat(is(earlier, later, "anx_I")).isEmpty();
  }

  @Test
  void pairsAPointThatMovedOutOfThePointBeforeIt() {
    assertThat(was(pointsWithin("3"), pointsBeside("2"), "pnt_12.4.2.1"))
        .hasValue("pnt_12.4.2/pnt_12.4.2.1");
  }

  @Test
  void pairsAMovedOccurrenceWithoutANumByWhatItSays() {
    var earlier =
        text(
            occurrence("att", block("num", "ATTACHMENT 5-A")),
            occurrence(
                "att/level_1",
                block("heading", "THIRD COUNTRIES"),
                block("p", "The following third countries are recognised: Canada.")));
    var later =
        text(
            occurrence(
                "att",
                block("num", "ATTACHMENT 5-A"),
                occurrence("att/level_1", block("heading", "HOLD BAGGAGE")),
                occurrence(
                    "att/level_1/level_1",
                    block("heading", "THIRD COUNTRIES"),
                    block(
                        "p", "The following third countries are recognised: Canada and Serbia."))));

    // The occurrence with only a heading says nothing to pair it by.
    assertThat(was(earlier, later, "att/level_1")).isEmpty();
    assertThat(was(earlier, later, "att/level_1/level_1")).hasValue("att/level_1");
  }

  @Test
  void pairsAPointThatMovedOutOfOccurrencesFurtherUp() {
    // As 692/2008 from 16 May 2016 to 27 July 2017: point 3.2.12.2.1.11 of Annex XVIII is no
    // longer four occurrences down, but directly in the annex.
    var earlier = annexWithPointIn("3");
    var later =
        text(
            paragraph(
                "anx",
                "ANNEX",
                "",
                paragraph(
                    "anx/level_1",
                    "A",
                    "Equipment",
                    paragraph("anx/level_1/pnt_3.2", "3.2", "Standards for EDS"))),
            paragraph("anx/pnt_3.2.1", "3.2.1", "All EDS shall meet standard 3."));

    assertThat(was(earlier, later, "anx/pnt_3.2.1")).hasValue("anx/level_1/pnt_3.2/pnt_3.2.1");
  }

  @Test
  void pairsAMovedPointThatAnotherAnnexAlsoHad() {
    // As 692/2008 in 2017: Annex I also had a point 3.2.12.2.1.11, and lost it.
    var earlier =
        text(
            paragraph(
                "anx_I",
                "ANNEX I",
                "",
                paragraph("anx_I/pnt_3.2.1", "3.2.1", "All EDS shall meet standard 3.")),
            paragraph(
                "anx_II",
                "ANNEX II",
                "",
                paragraph(
                    "anx_II/pnt_3.2",
                    "3.2",
                    "Standards for EDS",
                    paragraph(
                        "anx_II/pnt_3.2/pnt_3.2.1", "3.2.1", "All EDS shall meet standard 3.")),
                paragraph("anx_II/pnt_3.3", "3.3", "Tests")));
    var later =
        text(
            paragraph("anx_I", "ANNEX I", ""),
            paragraph(
                "anx_II",
                "ANNEX II",
                "",
                paragraph("anx_II/pnt_3.2", "3.2", "Standards for EDS"),
                paragraph("anx_II/pnt_3.3", "3.3", "Tests"),
                paragraph("anx_II/pnt_3.2.1", "3.2.1", "All EDS shall meet standard 3.")));

    assertThat(was(earlier, later, "anx_II/pnt_3.2.1")).hasValue("anx_II/pnt_3.2/pnt_3.2.1");
    assertThat(is(earlier, later, "anx_I/pnt_3.2.1")).isEmpty();
  }

  @Test
  void pairsRenumberedPointWhenPointWasAlreadyRenumberedByEarlierAct() {
    var earlier =
        text(
            pointWithNum("1/pnt_12", "12.", "Old point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));
    var later =
        text(pointWithNum("1/pnt_13", "13.", "Old point twelve wording.", "ActY", "renumbered"));

    assertThat(was(earlier, later, "1/pnt_13")).hasValue("1/pnt_12");
    assertThat(is(earlier, later, "1/pnt_13")).isEmpty();
  }

  @Test
  void pairsRenumberedPointWhenTagIdAttributeDiffersFromEarlierOccurrence() {
    var earlier =
        text(
            pointWithNum(
                "1/pnt_12", "12.", "Old point twelve wording.", "ActX", "M001", "replaced"),
            pointWithNum(
                "1/pnt_13", "13.", "Point thirteen wording.", "ActX", "M001", "renumbered"));
    var later =
        text(
            pointWithNum(
                "1/pnt_13", "13.", "Old point twelve wording.", "ActX", "M002", "renumbered"));

    assertThat(was(earlier, later, "1/pnt_13")).hasValue("1/pnt_12");
    assertThat(is(earlier, later, "1/pnt_13")).isEmpty();
  }

  @Test
  void pairsOccurrenceWithLeftoverRenumberedTagUnderSameIdentifier() {
    var earlier =
        text(
            pointWithNum("1/pnt_12", "12.", "Point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));
    var later =
        text(
            pointWithNum("1/pnt_12", "12.", "Point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));

    assertThat(was(earlier, later, "1/pnt_12")).hasValue("1/pnt_12");
    assertThat(was(earlier, later, "1/pnt_13")).hasValue("1/pnt_13");
  }

  @Test
  void pairsOccurrenceWithLeftoverRenumberedTagUnderSameIdentifierWhenWordingChanges() {
    var earlier =
        text(pointWithNum("1/pnt_13", "13.", "Point thirteen old text.", "ActX", "renumbered"));
    var later =
        text(pointWithNum("1/pnt_13", "13.", "Point thirteen new text.", "ActX", "renumbered"));

    assertThat(was(earlier, later, "1/pnt_13")).hasValue("1/pnt_13");
  }

  /**
   * The eId of the occurrence of {@code earlier} that the occurrence {@code eId} of {@code later}
   * pairs with.
   */
  private static Optional<String> was(Element earlier, Element later, String eId) {
    var before = Occurrences.of(earlier);
    var after = Occurrences.of(later);
    return Optional.ofNullable(
            Pairing.of(before, after).was(Objects.requireNonNull(after.get(eId), eId)))
        .map(Occurrence::eId);
  }

  /**
   * The eId of the occurrence of {@code later} that the occurrence {@code eId} of {@code earlier}
   * pairs with.
   */
  private static Optional<String> is(Element earlier, Element later, String eId) {
    var before = Occurrences.of(earlier);
    var after = Occurrences.of(later);
    return Optional.ofNullable(
            Pairing.of(before, after).is(Objects.requireNonNull(before.get(eId), eId)))
        .map(Occurrence::eId);
  }

  /** An annex with point 3.2.1 in point 3.2, in an occurrence A. */
  private static Element annexWithPointIn(String standard) {
    return text(
        paragraph(
            "anx",
            "ANNEX",
            "",
            paragraph(
                "anx/level_1",
                "A",
                "Equipment",
                paragraph(
                    "anx/level_1/pnt_3.2",
                    "3.2",
                    "Standards for EDS",
                    paragraph(
                        "anx/level_1/pnt_3.2/pnt_3.2.1",
                        "3.2.1",
                        "All EDS shall meet standard " + standard + ".")))));
  }

  /** Point 12.4.2 with its points 12.4.2.1 and 12.4.2.2 after it, as its siblings. */
  private static Element pointsBeside(String standard) {
    return text(
        paragraph("pnt_12.4.2", "12.4.2", "Standards for EDS"),
        paragraph("pnt_12.4.2.1", "12.4.2.1", "All EDS shall meet standard " + standard + "."),
        paragraph("pnt_12.4.2.2", "12.4.2.2", "Standard 2 shall expire in 2020."));
  }

  /** Point 12.4.2 with its points 12.4.2.1 and 12.4.2.2 in it. */
  private static Element pointsWithin(String standard) {
    return text(
        paragraph(
            "pnt_12.4.2",
            "12.4.2",
            "Standards for EDS",
            paragraph(
                "pnt_12.4.2/pnt_12.4.2.1",
                "12.4.2.1",
                "All EDS shall meet standard " + standard + "."),
            paragraph("pnt_12.4.2/pnt_12.4.2.2", "12.4.2.2", "Standard 2 shall expire in 2020.")));
  }
}
