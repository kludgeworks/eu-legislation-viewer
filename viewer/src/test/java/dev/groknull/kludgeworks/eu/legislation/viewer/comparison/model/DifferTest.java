// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Differ.Compared;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link Differ} compares the consolidations of 2015/1998 of 1 January, 22 March and 1
 * October 2026. The texts are as the content module reads them back. 2026/449 applies from 22
 * March. Its points on screening of in-flight supplies and airport supplies apply from 1 October.
 *
 * <p>The tests write out a marked element as it reads. Inserted words show as [+so] and deleted
 * words as [-so].
 */
class DifferTest {
  static final Element JANUARY = text("02015R1998-20260101");
  static final Element MARCH = text("02015R1998-20260322");
  static final Element OCTOBER = text("02015R1998-20261001");

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

  @Test
  void replacesAPhraseAsOneDeletionAndOneInsertion() {
    var g = Differ.differences(MARCH, OCTOBER).differences().get(2);
    assertThat(said(only(g.shown(), "p")))
        .isEqualTo(
            "EVD equipment applied in accordance with[+ Attachment 6-J to] the [-relevant"
                + " provisions contained in Attachment 6-J][+Annex to Implementing Decision C(2015)"
                + " 8005,] and in combination with point (a)[+ of this point].");
    assertThat(g.inserted()).isEqualTo(14);
    assertThat(g.deleted()).isEqualTo(7);
  }

  @Test
  void keepsAPhraseInsertedWholeThoughItRepeatsAWordAfterIt() {
    var e = Differ.differences(MARCH, OCTOBER).differences().get(1);
    assertThat(said(only(e.shown(), "p")))
        .isEqualTo(
            "ETD equipment[+ applied in accordance with Attachment 6-J to the Annex to Implementing"
                + " Decision C(2015) 8005, and] in combination with point (a)[+ of this point];");
  }

  @Test
  void keepsTheTextsStructureAroundItsMarks() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst().shown();
    assertThat(c.children()).extracting(Element::name).containsExactly("provenance", "num", "p");
    assertThat(descendants(c))
        .filteredOn(element -> element.name().startsWith("diff:"))
        .allSatisfy(
            mark -> assertThat(mark.children()).extracting(Element::name).containsOnly("t"));
  }

  @Test
  void keepsTheListAPointIsAnItemOfWithoutItsItems() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    var list = Objects.requireNonNull(c.list());
    assertThat(list.name()).isEqualTo("list");
    assertThat(list.attribute("kind")).isNotBlank();
    assertThat(list.children()).isEmpty();
  }

  @Test
  void leadsADifferenceWithTheOpeningOfTheUnitItIsIn() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    assertThat(c.parent()).isEqualTo("anx/pnt_8.1.2.3");
    assertThat(Objects.requireNonNull(c.lead()).children())
        .extracting(Wording::of)
        .containsExactly(
            "8.1.2.3",
            "The following means or method of screening, either individually or in combination,"
                + " shall be applied:");
  }

  @Test
  void foldsTheUnitsBetweenTwoDifferencesThatAreTheSame() {
    var differences = Differ.differences(MARCH, OCTOBER).differences();
    assertThat(differences.get(0).context().getLast().units())
        .extracting(Sibling::num)
        .containsExactly("(a)", "(b)");
    assertThat(differences.get(1).context())
        .singleElement()
        .satisfies(fold -> assertThat(fold.opening()).isNull())
        .extracting(Context::units)
        .asInstanceOf(InstanceOfAssertFactories.list(Sibling.class))
        .extracting(Sibling::eId, Sibling::kind, Sibling::num)
        .containsExactly(tuple("anx/pnt_8.1.2.3/pnt_d", "point", "(d)"));
  }

  @Test
  void placesEachUnitOfTheLaterTextOnceInOrder() {
    assertThat(placed(Differ.differences(MARCH, OCTOBER), OCTOBER))
        .containsExactlyElementsOf(Units.of(OCTOBER).byEId().keySet());
    assertThat(placed(Differ.differences(JANUARY, MARCH), MARCH))
        .containsExactlyElementsOf(Units.of(MARCH).byEId().keySet());
  }

  @Test
  void foldsTheUnitsAfterADifferenceToTheNextAndAfterTheLast() {
    var compared = Differ.differences(MARCH, OCTOBER);
    // The folds hold the last point of 8.1.2.3, (g), then 8.1.2.4 up to 9.1.2.3. After the last
    // difference, 9.1.2.4 up to the end is folded.
    var between = compared.differences().get(3).context();
    assertThat(between.getFirst().units())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_8.1.2.4");
    assertThat(between)
        .filteredOn(each -> each.opening() != null)
        .extracting(each -> each.units().getFirst().eId())
        .containsExactly("anx/pnt_9.1.2.3");
    assertThat(compared.closing().getFirst().units())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_9.1.2.4");
  }

  @Test
  void opensEachUnitThatHoldsDifferencesOnceWhereTheyEnterIt() {
    var compared = Differ.differences(JANUARY, MARCH);
    var opened =
        Stream.concat(
                compared.differences().stream().flatMap(d -> d.context().stream()),
                compared.closing().stream())
            .filter(each -> each.opening() != null)
            .map(each -> each.units().getFirst().eId())
            .toList();
    assertThat(opened).doesNotHaveDuplicates().contains("anx");
  }

  @Test
  void showsAUnitInsertedOrDeletedOnceWholeWithItsOwnUnits() {
    var differences = Differ.differences(JANUARY, MARCH).differences();
    // A changed point and the points in it that the same act changed, inserted or deleted count
    // as one difference.
    assertThat(differences).filteredOn(d -> d.kind() == Kind.INSERTED).hasSize(18);
    // Points that the later text keeps struck through count as deleted.
    assertThat(differences).filteredOn(d -> d.kind() == Kind.DELETED).hasSize(15);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.CHANGED).hasSize(34);
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .contains(tuple("anx/pnt_8.1.2.4", Kind.INSERTED), tuple("anx/pnt_9.1.3.6", Kind.DELETED))
        .containsSubsequence(
            tuple("anx/pnt_1.2.2.3", Kind.CHANGED),
            tuple("anx/pnt_9.1.3.5", Kind.CHANGED),
            tuple("anx/pnt_9.1.3.6", Kind.DELETED));
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("anx/pnt_1.2.2.3/pnt_a", "anx/pnt_9.1.3.5/pnt_a");
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("anx/pnt_9.1.3.5/pnt_b/idt_1");
    var inserted = find(differences, "anx/pnt_8.1.2.4");
    assertThat(unmarked(inserted.shown())).isBlank();
    assertThat(said(inserted.shown())).startsWith("[+").doesNotContain("[-");
    assertThat(inserted.deleted()).isZero();
    var deleted = find(differences, "anx/pnt_9.1.3.6");
    assertThat(unmarked(deleted.shown())).isBlank();
    assertThat(said(deleted.shown())).startsWith("[-").doesNotContain("[+");
    assertThat(deleted.inserted()).isZero();
  }

  @Test
  void readsAUnitTheLaterTextKeepsStruckAsDeletedByTheActThatStruckIt() {
    var struck = find(Differ.differences(JANUARY, MARCH).differences(), "anx/pnt_6.8.1.6");
    assertThat(struck.kind()).isEqualTo(Kind.DELETED);
    assertThat(struck.markers()).containsExactly(new Marker("32026R0449", "deleted", null));
    assertThat(Units.of(MARCH).get("anx/pnt_6.8.1.6")).isNull();
  }

  @Test
  void showsAChangedUnitWithItsOwnContentOnlyWhereOtherActsChangedTheUnitsInIt() {
    var earlier = text(point("1", "Old words", "A", point("1/a", "(a) old", "A")));
    var later = text(point("1", "New words", "B", point("1/a", "(a) new", "C")));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).extracting(Difference::eId).containsExactly("1", "1/a");
    assertThat(descendants(differences.getFirst().shown()))
        .extracting(Element::name)
        .doesNotContain("unit");
  }

  @Test
  void showsAChangedUnitWholeWithTheUnitsInItThatTheSameActsChangedOrDeleted() {
    var earlier =
        text(
            point(
                "1",
                "Old words",
                "A",
                point("1/a", "(a) old", "A"),
                point("1/b", "(b) gone", "A")));
    var later = text(point("1", "New words", "B", point("1/a", "(a) new", "B")));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).singleElement().extracting(Difference::eId).isEqualTo("1");
    var shown = differences.getFirst().shown();
    assertThat(descendants(shown))
        .filteredOn(each -> each.name().equals("unit"))
        .extracting(each -> each.attribute("eId"))
        .containsExactly("1/a", "1/b");
    assertThat(said(shown)).contains("[+New]", "[+new]", "[-old]").containsPattern("\\[-[^]]*gone");
    // The deleted words are Old, old and the words of point (b).
    assertThat(differences.getFirst().deleted()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void findsNoDifferenceInANumThatOnlyGainedAFullStop() {
    var earlier = text(numbered("11.3", "CERTIFICATION"));
    var later = text(numbered("11.3.", "CERTIFICATION"));

    assertThat(Differ.differences(earlier, later).differences()).isEmpty();
    assertThat(Differ.differences(earlier, text(numbered("11.4", "CERTIFICATION"))).differences())
        .hasSize(1);
  }

  @Test
  void pairsRenumberedSiblingPointsWhenNewPointIsInserted() {
    var earlier =
        text(
            pointWithNum("1/pnt_a", "(a)", "alpha text", "A", "replaced"),
            pointWithNum("1/pnt_b", "(b)", "known supplier of airport supplies", "A", "replaced"));
    var later =
        text(
            pointWithNum("1/pnt_a", "(a)", "alpha text", "A", "replaced"),
            pointWithNum(
                "1/pnt_b", "(b)", "regulated supplier of airport supplies", "B", "inserted"),
            pointWithNum(
                "1/pnt_c", "(c)", "known supplier of airport supplies", "B", "renumbered"));

    var differences = Differ.differences(earlier, later).differences();

    var inserted = find(differences, "1/pnt_b");
    assertThat(inserted.kind()).isEqualTo(Kind.INSERTED);
    assertThat(said(inserted.shown())).contains("[+regulated supplier of airport supplies]");

    var renumbered = find(differences, "1/pnt_c");
    assertThat(renumbered.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(renumbered.shown(), "p"))).isEqualTo("known supplier of airport supplies");
    assertThat(said(only(renumbered.shown(), "num"))).contains("[-b]", "[+c]");
  }

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

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_b", Kind.DELETED), tuple("1/pnt_b", Kind.CHANGED));
    assertThat(differences).extracting(Difference::eId).doesNotContain("1/pnt_b/sub_1");
    var deleted = differences.getFirst();
    assertThat(said(deleted.shown())).contains("[-Bravo]", "[-Item 1]");
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

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_a", Kind.INSERTED), tuple("1/pnt_c", Kind.CHANGED));
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("1/pnt_b/sub_1", "1/pnt_c/sub_1");
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

    var differences = Differ.differences(earlier, later).differences();

    var renumbered = find(differences, "1/pnt_b");
    assertThat(renumbered.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(renumbered.shown(), "p"))).contains("[-carried out]", "[+conducted]");
  }

  @Test
  void showsPointReplacedWhereItStandsAsChangedUnderOwnEId() {
    var earlier = text(pointWithNum("1/pnt_a", "(a)", "Alpha words", "A", "replaced"));
    var later = text(pointWithNum("1/pnt_a", "(a)", "Completely new wording", "B", "replaced"));

    var differences = Differ.differences(earlier, later).differences();

    var replaced = find(differences, "1/pnt_a");
    assertThat(replaced.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(replaced.shown(), "p")))
        .contains("[-Alpha words]", "[+Completely new wording]");
  }

  @Test
  void countsTotalsOfWholeUnitContainingRenumberedUnit() {
    var earlier =
        text(
            point(
                "1",
                "Old lead text",
                "A",
                pointWithNum("1/pnt_a", "(a)", "Point a text", "A", "replaced"),
                pointWithNum("1/pnt_b", "(b)", "Point b text", "A", "replaced")));
    var later =
        text(
            point(
                "1",
                "New lead text",
                "B",
                pointWithNum("1/pnt_c", "(c)", "Point b text", "B", "renumbered")));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).singleElement().extracting(Difference::eId).isEqualTo("1");
    var whole = differences.getFirst();
    assertThat(whole.inserted()).isGreaterThan(0);
    assertThat(whole.deleted()).isGreaterThan(0);
    assertThat(said(whole.shown())).contains("[-Point a text]");
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

    var differences = Differ.differences(earlier, later).differences();

    var renumbered = find(differences, "1/pnt_b");
    assertThat(renumbered.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(renumbered.shown(), "p"))).contains("[+ και εκρηκτικών]");
    assertThat(said(only(renumbered.shown(), "num"))).contains("[-α]", "[+β]");
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

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_b", Kind.DELETED), tuple("1/pnt_b", Kind.CHANGED));
    var deleted = differences.getFirst();
    assertThat(said(deleted.shown()))
        .contains("[-The competent authority shall conduct security screening and inspections.]");
    var changed = differences.get(1);
    assertThat(said(only(changed.shown(), "p"))).contains("[+ and rules]");
    assertThat(said(only(changed.shown(), "num"))).contains("[-c]", "[+b]");
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

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_b", Kind.DELETED), tuple("1/pnt_b", Kind.INSERTED));
    var deleted = differences.getFirst();
    assertThat(said(deleted.shown()))
        .contains("[-Security rules for maritime ports and terminal facilities.]");
    var inserted = differences.get(1);
    assertThat(said(inserted.shown()))
        .contains("[+Civil aviation operations conducted by licensed commercial air carriers.]");
  }

  @Test
  void pairsRenumberedPointsInRealJointInvestigationTeamsFixture() {
    var earlier = text("02017G0119-20220128");
    var later = text("02017G0119-20260429");

    var differences = Differ.differences(earlier, later).differences();

    var insertedPoint12 = find(differences, "anx/pnt_12");
    assertThat(insertedPoint12.kind()).isEqualTo(Kind.INSERTED);

    var renumberedPoint13 = find(differences, "anx/pnt_13");
    assertThat(renumberedPoint13.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(renumberedPoint13.shown(), "num"))).isEqualTo("[-12][+13].");

    var renumberedPoint19 = find(differences, "anx/pnt_19");
    assertThat(renumberedPoint19.kind()).isEqualTo(Kind.CHANGED);
    assertThat(said(only(renumberedPoint19.shown(), "num"))).isEqualTo("[-18][+19].");
  }

  @Test
  void pairsUnitsInRealHighRiskPlantsFixture() {
    var earlier = text("02018R2019-20250220");
    var later = text("02018R2019-20250623");

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).extracting(Difference::eId).doesNotContain("anx");
    var changedPoint = find(differences, "anx/level_1/pnt_1");
    assertThat(changedPoint.kind()).isEqualTo(Kind.CHANGED);
  }

  @Test
  void pairsUnitsInRealSanctionsDecisionFixture() {
    var earlier = text("02022D2319-20241216");
    var later = text("02022D2319-20250326");

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).extracting(Difference::eId).doesNotContain("anx_I");
    var changedArticle = find(differences, "art_1/par_1");
    assertThat(changedArticle.kind()).isEqualTo(Kind.CHANGED);
  }

  @Test
  void pairsStructureLevelUnitRenumberedViaProvenance() {
    var earlier =
        text(
            annexWithProvenance(
                "anx_I", "ANNEX I", "Provisions on security screening.", "A", "replaced"),
            annexWithProvenance(
                "anx_II", "ANNEX II", "Provisions on chemical safety.", "A", "replaced"));
    var later =
        text(
            annexWithProvenance(
                "anx_I", "ANNEX I", "Provisions on chemical safety.", "B", "renumbered"));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("anx_I", Kind.DELETED), tuple("anx_I", Kind.CHANGED));
    var deleted = differences.getFirst();
    assertThat(said(deleted.shown())).contains("[-Provisions on security screening.]");
    var changed = differences.get(1);
    assertThat(said(only(changed.shown(), "p"))).isEqualTo("Provisions on chemical safety.");
    assertThat(said(only(changed.shown(), "num"))).isEqualTo("ANNEX [-II][+I]");
  }

  @Test
  void resolvesAmendingActFromRenumberedAncestorForDeletedUnit() {
    var earlier =
        text(
            articleWithReplacedAndRenumbered(
                "art_5",
                "Article 5",
                "General rules and framework on transport safety across the Union.",
                "ActA",
                "",
                paragraph(
                    "art_5/par_1",
                    "1.",
                    "Scope.",
                    pointWithNum(
                        "art_5/par_1/pnt_a",
                        "(a)",
                        "Deleted point on inspection requirements.",
                        "ActA",
                        "replaced"))),
            articleWithReplacedAndRenumbered(
                "art_6",
                "Article 6",
                "Special security standards and oversight for international ports.",
                "ActA",
                "",
                paragraph("art_6/par_1", "1.", "Security provisions.")));

    var later =
        text(
            articleWithReplacedAndRenumbered(
                "art_6",
                "Article 6",
                "General rules and framework on transport safety across the Union.",
                "ActX",
                "ActX",
                paragraph("art_6/par_1", "1.", "Scope.")),
            articleWithReplacedAndRenumbered(
                "art_7",
                "Article 7",
                "Special security standards and oversight for international ports.",
                "WrongAct",
                "ActX",
                paragraph("art_7/par_1", "1.", "Security provisions.")));

    var differences = Differ.differences(earlier, later).differences();

    var deletedPoint = find(differences, "art_5/par_1/pnt_a");
    assertThat(deletedPoint.kind()).isEqualTo(Kind.DELETED);
    assertThat(deletedPoint.markers()).containsExactly(new Marker("ActX", "replaced", "art_6"));
  }

  @Test
  void pairsRenumberedPointWhenPointWasAlreadyRenumberedByEarlierAct() {
    var earlier =
        text(
            pointWithNum("1/pnt_12", "12.", "Old point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));
    var later =
        text(pointWithNum("1/pnt_13", "13.", "Old point twelve wording.", "ActY", "renumbered"));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_13", Kind.CHANGED), tuple("1/pnt_13", Kind.DELETED));
    var changed = differences.getFirst();
    assertThat(said(only(changed.shown(), "p"))).isEqualTo("Old point twelve wording.");
    assertThat(said(only(changed.shown(), "num"))).isEqualTo("[-12][+13].");
    var deleted = differences.get(1);
    assertThat(said(deleted.shown())).contains("[-Point thirteen wording.]");
  }

  @Test
  void pairsRenumberedPointWhenMarkerAttributeDiffersFromEarlierUnit() {
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

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_13", Kind.CHANGED), tuple("1/pnt_13", Kind.DELETED));
    var changed = differences.getFirst();
    assertThat(said(only(changed.shown(), "p"))).isEqualTo("Old point twelve wording.");
    assertThat(said(only(changed.shown(), "num"))).isEqualTo("[-12][+13].");
    var deleted = differences.get(1);
    assertThat(said(deleted.shown())).contains("[-Point thirteen wording.]");
  }

  @Test
  void pairsUnitWithLeftoverRenumberedMarkerUnderSameIdentifier() {
    var earlier =
        text(
            pointWithNum("1/pnt_12", "12.", "Point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));
    var later =
        text(
            pointWithNum("1/pnt_12", "12.", "Point twelve wording.", "ActW", "replaced"),
            pointWithNum("1/pnt_13", "13.", "Point thirteen wording.", "ActX", "renumbered"));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences).isEmpty();
  }

  @Test
  void showsUnitWithLeftoverRenumberedMarkerAsChangedUnderSameIdentifierWhenWordingChanges() {
    var earlier =
        text(pointWithNum("1/pnt_13", "13.", "Point thirteen old text.", "ActX", "renumbered"));
    var later =
        text(pointWithNum("1/pnt_13", "13.", "Point thirteen new text.", "ActX", "renumbered"));

    var differences = Differ.differences(earlier, later).differences();

    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(tuple("1/pnt_13", Kind.CHANGED));
    var changed = differences.getFirst();
    assertThat(said(only(changed.shown(), "p"))).isEqualTo("Point thirteen [-old][+new] text.");
    assertThat(said(only(changed.shown(), "num"))).isEqualTo("13.");
  }

  /**
   * A point {@code eId} with the words {@code words} and the units {@code units}. The act {@code
   * act} replaced it.
   */
  private static Element point(String eId, String words, String act, Element... units) {
    var children = new ArrayList<Element>();
    children.add(
        new Element("provenance", Map.of("amendingAct", act, "action", "replaced"), List.of(), ""));
    children.add(block("p", words));
    children.addAll(List.of(units));
    return new Element("unit", Map.of("eId", eId, "kind", "point"), children, "");
  }

  /**
   * A point {@code eId} with the number {@code num}, the words {@code words}, amending act {@code
   * act}, action {@code action}, and child units {@code units}.
   */
  private static Element pointWithNum(
      String eId, String num, String words, String act, String action, Element... units) {
    return pointWithNum(eId, num, words, act, "", action, units);
  }

  /**
   * A point {@code eId} with the number {@code num}, the words {@code words}, amending act {@code
   * act}, marker identifier {@code marker}, action {@code action}, and child units {@code units}.
   */
  private static Element pointWithNum(
      String eId,
      String num,
      String words,
      String act,
      String marker,
      String action,
      Element... units) {
    var children = new ArrayList<Element>();
    if (!act.isEmpty() && !action.isEmpty() && !"renumbered".equals(action)) {
      var attrs = new HashMap<String, String>();
      attrs.put("amendingAct", act);
      attrs.put("action", action);
      if (!marker.isEmpty()) {
        attrs.put("marker", marker);
      }
      children.add(new Element("provenance", attrs, List.of(), ""));
    }
    Element numBlock;
    if ("renumbered".equals(action)) {
      var attrs = new HashMap<String, String>();
      attrs.put("action", "renumbered");
      attrs.put("amendingAct", act);
      if (!marker.isEmpty()) {
        attrs.put("marker", marker);
      }
      var changed =
          new Element(
              "changed", attrs, List.of(new Element("t", Map.of("v", num), List.of(), "")), "");
      numBlock = new Element("num", Map.of(), List.of(changed), "");
    } else {
      numBlock = block("num", num);
    }
    children.add(numBlock);
    children.add(block("p", words));
    children.addAll(List.of(units));
    return new Element("unit", Map.of("eId", eId, "kind", "point"), children, "");
  }

  /** An annex {@code eId} with provenance {@code act} and {@code action}. */
  private static Element annexWithProvenance(
      String eId, String num, String words, String act, String action, Element... units) {
    var children = new ArrayList<Element>();
    if (act != null && action != null) {
      children.add(
          new Element("provenance", Map.of("amendingAct", act, "action", action), List.of(), ""));
    }
    children.add(block("num", num));
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(units));
    return new Element("unit", Map.of("eId", eId, "kind", "annex"), children, "");
  }

  /** An article with replaced provenance and renumbered num. */
  private static Element articleWithReplacedAndRenumbered(
      String eId,
      String num,
      String words,
      String replacedAct,
      String renumberedAct,
      Element... units) {
    var children = new ArrayList<Element>();
    if (!replacedAct.isEmpty()) {
      children.add(
          new Element(
              "provenance",
              Map.of("amendingAct", replacedAct, "action", "replaced"),
              List.of(),
              ""));
    }
    Element numBlock;
    if (!renumberedAct.isEmpty()) {
      var changed =
          new Element(
              "changed",
              Map.of("action", "renumbered", "amendingAct", renumberedAct),
              List.of(new Element("t", Map.of("v", num), List.of(), "")),
              "");
      numBlock = new Element("num", Map.of(), List.of(changed), "");
    } else {
      numBlock = block("num", num);
    }
    children.add(numBlock);
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(units));
    return new Element("unit", Map.of("eId", eId, "kind", "article"), children, "");
  }

  /** A paragraph {@code eId} with number {@code num} and words {@code words}. */
  private static Element paragraph(String eId, String num, String words, Element... units) {
    var children = new ArrayList<Element>();
    children.add(block("num", num));
    if (!words.isEmpty()) {
      children.add(block("p", words));
    }
    children.addAll(List.of(units));
    return new Element("unit", Map.of("eId", eId, "kind", "paragraph"), children, "");
  }

  private static Element numbered(String num, String heading) {
    return new Element(
        "unit",
        Map.of("eId", "11.3", "kind", "level"),
        List.of(block("num", num), block("heading", heading)),
        "");
  }

  private static Element block(String name, String words) {
    return new Element(
        name, Map.of(), List.of(new Element("t", Map.of("v", words), List.of(), "")), "");
  }

  private static Element text(Element... units) {
    return new Element("body", Map.of(), List.of(units), "");
  }

  @Test
  void comparesATableRowByRowAndCellByCell() {
    var differences =
        Differ.differences(text("02018R2019-20250220"), text("02018R2019-20250623")).differences();
    // A list inserted in the cell of Alnus renumbers the lists in the cells after it, list_2 to
    // list_3 and so on. Their units are not differences of their own.
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

  @Test
  void showsARowDeletedFromATableAsARowWhereItStood() {
    var from = text(unit("art_1", table(row("A", "1"), row("B", "2"), row("C", "3"))));
    var to = text(unit("art_1", table(row("A", "1"), row("C", "3"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows).extracting(DifferTest::said).containsExactly("A1", "[-B][-2]", "C3");
    assertThat(difference.deleted()).isEqualTo(2);
  }

  @Test
  void pairsAChangedRowWithTheRowItWasNotWithARowInsertedBeforeIt() {
    var from =
        text(
            unit(
                "art_1",
                table(
                    row("Acacia", "all third countries"),
                    row("Alnus", "all third countries"),
                    row("Betula", "all third countries"))));
    var to =
        text(
            unit(
                "art_1",
                table(
                    row("Acacia", "all third countries"),
                    row("Albizia", "Israel"),
                    row("Alnus", "all third countries other than the United Kingdom"),
                    row("Betula", "all third countries"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows)
        .extracting(DifferTest::said)
        .containsExactly(
            "Acaciaall third countries",
            "[+Albizia][+Israel]",
            "Alnusall third countries[+ other than the United Kingdom]",
            "Betulaall third countries");
  }

  @Test
  void pairsAChangedBlockWithTheBlockItWasNotWithABlockInsertedBeforeIt() {
    var from =
        text(
            unit(
                "art_1",
                block("num", "1."),
                block("p", "Member States shall notify the Commission."),
                block("p", "This Regulation shall apply from 1 January 2026.")));
    var to =
        text(
            unit(
                "art_1",
                block("num", "1."),
                block("p", "Operators shall keep records for five years."),
                block("p", "Member States shall notify the Commission without delay."),
                block("p", "This Regulation shall apply from 1 January 2026.")));
    var shown = Differ.differences(from, to).differences().getFirst().shown();
    assertThat(shown.children())
        .extracting(DifferTest::said)
        .containsExactly(
            "1.",
            "[+Operators shall keep records for five years.]",
            "Member States shall notify the Commission[+ without delay].",
            "This Regulation shall apply from 1 January 2026.");
  }

  @Test
  void comparesAChangedCellWithTheCellAboveIt() {
    var from = text(unit("art_1", table(row("A", "1"), row("B", "2"))));
    var to = text(unit("art_1", table(row("A", "1"), row("B", "20"))));
    var difference = Differ.differences(from, to).differences().getFirst();
    var rows = only(difference.shown(), "table").children();
    assertThat(rows.get(1).children())
        .extracting(DifferTest::said)
        .containsExactly("B", "[-2][+20]");
  }

  @Test
  void readsAChangeOfSpacesAloneAsNoChange() {
    // The consolidation of 26 March 2025 has "DOB:30 March 1977", without the space after the
    // colon.
    var differences =
        Differ.differences(text("02022D2319-20241216"), text("02022D2319-20250326")).differences();
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("anx_I/level_1/list_1/pnt_1", "anx_I/level_1/list_2/pnt_3");
  }

  @Test
  void showsAChangedFormulaWholeAsDeletedThenInserted() {
    var from = text(unit("art_1", formula("1")));
    var to = text(unit("art_1", formula("2")));
    var difference = Differ.differences(from, to).differences().getFirst();
    var p = only(difference.shown(), "p");
    assertThat(p.children()).extracting(Element::name).containsExactly("t", "diff:del", "diff:ins");
    assertThat(p.children().get(1).children()).extracting(Element::name).containsExactly("math");
    assertThat(p.children().get(2).children()).extracting(Element::name).containsExactly("math");
  }

  @Test
  void showsANoteAnActChangedUnderTheUnitThatRefersToIt() {
    // As 2004/796 changed its note to Silesia: the text that refers to it is the same.
    var from =
        withNotes(
            List.of(unit("art_1", cites("Silesia", "note_1"))), note("note_1", "only in Poland"));
    var to =
        withNotes(
            List.of(unit("art_1", cites("Silesia", "note_1"))),
            note("note_1", "Only in Poland, as authorised by Decision 2004/297/EC"));
    var differences = Differ.differences(from, to).differences();
    assertThat(differences).extracting(Difference::eId).containsExactly("art_1");
    var notes = only(differences.getFirst().shown(), "notes");
    assertThat(said(notes))
        .isEqualTo("[-only][+Only] in Poland[+, as authorised by Decision 2004/297/EC]");
  }

  @Test
  void matchesANoteThroughTheReferenceToItNotByItsPlace() {
    // A note inserted before it renumbers the note of art_2, note_1 to note_2. Its words are the
    // same.
    var from =
        withNotes(
            List.of(unit("art_1", block("p", "Plain.")), unit("art_2", cites("Cited", "note_1"))),
            note("note_1", "OJ L 97, 1.4.2004, p. 66."));
    var to =
        withNotes(
            List.of(
                unit("art_1", cites("Plain", "note_1")), unit("art_2", cites("Cited", "note_2"))),
            note("note_1", "OJ L 1, 1.1.2026, p. 1."),
            note("note_2", "OJ L 97, 1.4.2004, p. 66."));
    var differences = Differ.differences(from, to).differences();
    assertThat(differences).extracting(Difference::eId).containsExactly("art_1");
    assertThat(said(only(differences.getFirst().shown(), "notes")))
        .isEqualTo("[+OJ L 1, 1.1.2026, p. 1.]");
  }

  @Test
  void findsTheNotesOfAnActAsPublishedAtTheEndOfItsAnnex() {
    // As 2015/1998 as published: the note of point 1.0.5 is at the end of the annex. Its
    // consolidations have it at the end of the text, with another eId. Its words are the same.
    var annex =
        new Element(
            "unit",
            Map.of("eId", "anx", "kind", "annex"),
            List.of(
                unit("anx/pnt_1.0.5", cites("References", "note_anx01000301-2")),
                new Element(
                    "notes",
                    Map.of(),
                    List.of(note("note_anx01000301-2", "Decision C(2015) 8005 final.")),
                    "")),
            "");
    var from = withNotes(List.of(annex));
    var to =
        withNotes(
            List.of(
                new Element(
                    "unit",
                    Map.of("eId", "anx", "kind", "annex"),
                    List.of(unit("anx/pnt_1.0.5", cites("References", "note_2"))),
                    "")),
            note("note_2", "Decision C(2015) 8005 final."));
    assertThat(Differ.differences(from, to).differences()).isEmpty();
  }

  @Test
  void showsTheNotesOfAnInsertedUnitInsertedWithIt() {
    var from = withNotes(List.of(unit("art_1", block("p", "Plain."))));
    var to =
        withNotes(
            List.of(unit("art_1", block("p", "Plain.")), unit("art_2", cites("New", "note_1"))),
            note("note_1", "OJ L 1, 1.1.2026, p. 1."));
    var inserted = Differ.differences(from, to).differences().getFirst();
    assertThat(inserted.kind()).isEqualTo(Kind.INSERTED);
    assertThat(said(only(inserted.shown(), "notes"))).isEqualTo("[+OJ L 1, 1.1.2026, p. 1.]");
  }

  /** A text with the units {@code units} in its body, and the notes {@code notes} at its end. */
  private static Element withNotes(List<Element> units, Element... notes) {
    return new Element(
        "legalText",
        Map.of(),
        List.of(
            new Element("body", Map.of(), units, ""),
            new Element("notes", Map.of(), List.of(notes), "")),
        "");
  }

  private static Element note(String eId, String words) {
    return new Element(
        "note",
        Map.of("eId", eId, "marker", eId.substring(eId.indexOf('_') + 1), "kind", "footnote"),
        List.of(block("p", words)),
        "");
  }

  /** A block that says {@code words} and refers to the note {@code eId}. */
  private static Element cites(String words, String eId) {
    return new Element(
        "p",
        Map.of(),
        List.of(
            new Element("t", Map.of("v", words), List.of(), ""),
            new Element(
                "noteRef", Map.of("href", "#" + eId, "marker", eId.substring(5)), List.of(), "")),
        "");
  }

  private static Element unit(String eId, Element... blocks) {
    return new Element("unit", Map.of("eId", eId, "kind", "article"), List.of(blocks), "");
  }

  private static Element table(Element... rows) {
    return new Element("table", Map.of(), List.of(rows), "");
  }

  private static Element row(String... cells) {
    var tds = new ArrayList<Element>();
    for (var cell : cells) {
      tds.add(new Element("td", Map.of(), List.of(block("p", cell)), ""));
    }
    return new Element("tr", Map.of(), tds, "");
  }

  /** A block that says "The rate is x + {@code n}", with x + n a formula. */
  private static Element formula(String n) {
    var math =
        new Element(
            "math",
            Map.of(),
            List.of(
                new Element("mi", Map.of(), List.of(), "x"),
                new Element("mo", Map.of(), List.of(), "+"),
                new Element("mn", Map.of(), List.of(), n)),
            "");
    return new Element(
        "p",
        Map.of(),
        List.of(new Element("t", Map.of("v", "The rate is "), List.of(), ""), math),
        "");
  }

  @Test
  void showsAChangedUnitWholeWithTheUnitsInItThatTheSameActsChanged() {
    var differences = Differ.differences(JANUARY, MARCH).differences();
    var changed = find(differences, "anx/pnt_1.2.2.3");
    assertThat(changed.kind()).isEqualTo(Kind.CHANGED);
    // 2026/449 inserted points (a) to (c) when it replaced 1.2.2.3. They show inside 1.2.2.3, not
    // as differences of their own.
    assertThat(descendants(changed.shown()))
        .filteredOn(each -> each.name().equals("unit"))
        .extracting(each -> each.attribute("eId"))
        .contains("anx/pnt_1.2.2.3/pnt_a", "anx/pnt_1.2.2.3/pnt_b", "anx/pnt_1.2.2.3/pnt_c");
    assertThat(differences).extracting(Difference::eId).doesNotContain("anx/pnt_1.2.2.3/pnt_a");
  }

  private static boolean holdsUnits(Element shown) {
    return descendants(shown).stream().anyMatch(each -> each.name().equals("unit"));
  }

  @Test
  void namesTheActsThatMarkAUnitInEachText() {
    var c = Differ.differences(MARCH, OCTOBER).differences().getFirst();
    assertThat(c.markers()).containsExactly(new Marker("32026R0449", "replaced", null));
    // In March the point has no marker, and it is in no unit that an act put in whole. So it is
    // as the act was published.
    assertThat(c.earlierMarkers()).isEmpty();
  }

  @Test
  void readsAPointWithoutMarkersAsTheActThatPutThePointItIsInWholePutIt() {
    var units = Units.of(MARCH);
    var a = Objects.requireNonNull(units.get("anx/pnt_1.2.2.3/pnt_a"));
    assertThat(Markers.of(a, units))
        .containsExactly(new Marker("32026R0449", "replaced", "anx/pnt_1.2.2.3"));
  }

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

  /**
   * {@code text} with a preamble of one recital before its content, as in the consolidation of
   * 2016.
   */
  private static Element withPreamble(Element text) {
    var recital =
        new Element(
            "unit",
            Map.of("eId", "pbl_1/rct_1", "kind", "recital"),
            List.of(new Element("p", Map.of(), List.of(), "Whereas:")),
            "");
    var preamble = new Element("preamble", Map.of("eId", "pbl_1"), List.of(recital), "");
    var children = new ArrayList<Element>();
    children.add(preamble);
    children.addAll(text.children());
    return new Element(text.name(), text.attributes(), children, "");
  }

  /**
   * The eIds of the units of the {@code later} text, in the order that {@code compared} places
   * them. The list has:
   *
   * <ul>
   *   <li>each difference, except a deleted one,
   *   <li>each unit opened or folded,
   *   <li>the units in a folded or inserted unit.
   * </ul>
   *
   * <p>A unit placed twice is in the list twice.
   */
  private static List<String> placed(Compared compared, Element later) {
    var units = Units.of(later);
    var placed = new ArrayList<String>();
    for (var difference : compared.differences()) {
      placed(difference.context(), units, placed);
      if (difference.kind() != Kind.DELETED) {
        placed.add(difference.eId());
      }
      // An inserted unit, or a changed unit shown whole, is placed with its units.
      if (difference.kind() != Kind.DELETED && holdsUnits(difference.shown())) {
        placed.addAll(inside(difference.eId(), units));
      }
    }
    placed(compared.closing(), units, placed);
    return placed;
  }

  private static void placed(List<Context> context, Units units, List<String> placed) {
    for (var each : context) {
      for (var unit : each.units()) {
        placed.add(unit.eId());
        if (each.opening() == null) {
          placed.addAll(inside(unit.eId(), units));
        }
      }
    }
  }

  /** The eIds of the units in the unit {@code eId}, in document order. */
  private static List<String> inside(String eId, Units units) {
    var inside = new ArrayList<String>();
    for (var unit : units.in(eId)) {
      inside.add(unit);
      inside.addAll(inside(unit, units));
    }
    return inside;
  }

  private static Difference find(List<Difference> differences, String eId) {
    return differences.stream().filter(d -> d.eId().equals(eId)).findFirst().orElseThrow();
  }

  /** The one child of {@code unit} with the name {@code name}. */
  private static Element only(Element unit, String name) {
    return unit.children().stream()
        .filter(child -> child.name().equals(name))
        .reduce(
            (one, other) -> {
              throw new AssertionError("More than one " + name + " in " + unit.attribute("eId"));
            })
        .orElseThrow();
  }

  /** What {@code element} says, with its marks written out as [+inserted] and [-deleted]. */
  private static String said(Element element) {
    var out = new StringBuilder();
    say(element, out);
    return out.toString();
  }

  private static void say(Element element, StringBuilder out) {
    switch (element.name()) {
      case "t" -> out.append(element.attribute("v"));
      case "diff:ins" -> {
        out.append("[+");
        element.children().forEach(child -> say(child, out));
        out.append(']');
      }
      case "diff:del" -> {
        out.append("[-");
        element.children().forEach(child -> say(child, out));
        out.append(']');
      }
      default -> element.children().forEach(child -> say(child, out));
    }
  }

  /** The words of {@code element} that are in no mark. */
  private static String unmarked(Element element) {
    if (element.name().startsWith("diff:")) {
      return "";
    }
    if (element.name().equals("t")) {
      return Objects.requireNonNullElse(element.attribute("v"), "");
    }
    var out = new StringBuilder();
    element.children().forEach(child -> out.append(unmarked(child)));
    return out.toString();
  }

  private static List<Element> descendants(Element element) {
    return element.children().stream()
        .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
        .toList();
  }

  private static Element text(String celex) {
    return Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
  }
}
