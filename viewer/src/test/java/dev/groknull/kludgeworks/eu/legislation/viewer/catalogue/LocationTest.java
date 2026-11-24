// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests how Cellar's locations of amendments are found among the occurrences of a text. The
 * location codes come from Cellar's annotations on 648/2012 (EMIR) and 2015/1998. The eIds come
 * from the texts of their consolidations.
 */
class LocationTest {
  static final List<String> EMIR =
      List.of(
          "tis_I",
          "art_1",
          "art_1/par_4",
          "art_1/par_4/pnt_c",
          "art_1/par_4/pnt_c/pnt_v",
          "art_11",
          "art_11/par_5",
          "art_11/par_15",
          "art_11/par_15/sub_1",
          "art_11/par_15/sub_1/pnt_a",
          "art_81",
          "art_81/par_3",
          "art_81/par_3/sub_1",
          "art_81/par_3/sub_1/pnt_r",
          "art_89",
          "art_89/par_1",
          "art_89/par_1/sub_1",
          "art_89/par_5a",
          "anx_I",
          "anx_I/level_1",
          "anx_I/level_1/pnt_I",
          "anx_I/level_1/pnt_I/pnt_i",
          "anx_I/level_1/pnt_II",
          "anx_I/level_1/pnt_II/pnt_i");

  static final List<String> AVSEC =
      List.of(
          "anx",
          "anx/pnt_6.3.1.4",
          "anx/pnt_8.1.2.3",
          "anx/pnt_8.1.2.3/pnt_c",
          "anx/pnt_11.3.2",
          "anx/pnt_11.3.3",
          "anx/pnt_11.3.4",
          "anx/pnt_11.3.5",
          "anx/pnt_12.4.2",
          "anx/pnt_12.4.2/pnt_12.4.2.4",
          "anx/level_ATTACHMENT-5-A");

  @Test
  void findsAnArticlesParagraphAndSubparagraph() {
    assertThat(in("AR 89 PA 1 ALN 1", EMIR)).contains(exact("art_89/par_1/sub_1"));
    assertThat(in("AR 1 PA 4 PO (c) PT (v)", EMIR)).contains(exact("art_1/par_4/pnt_c/pnt_v"));
  }

  @Test
  void findsAPointBelowASubparagraphCellarDoesntName() {
    assertThat(in("AR 81 PA 3 PTA (r)", EMIR)).contains(exact("art_81/par_3/sub_1/pnt_r"));
  }

  @Test
  void findsAnAnnexSectionAmongItsLevelsByItsOwnPointsFirst() {
    assertThat(in("AN I SCT I PTA (i)", EMIR)).contains(exact("anx_I/level_1/pnt_I/pnt_i"));
  }

  @Test
  void readsTheCodesCellarRunsTogether() {
    assertThat(in("AR 11.5", EMIR)).contains(exact("art_11/par_5"));
    assertThat(in("AR 89 .5BI", EMIR)).contains(exact("art_89/par_5a"));
  }

  @Test
  void findsTheOneAnnexsPointsAndAppendicesPastTheChaptersOurEIdsKeepFlat() {
    assertThat(in("AN PO 8.1.2.3 PTA (c)", AVSEC)).contains(exact("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(in("AN CHA 5 APP 5-A", AVSEC)).contains(exact("anx/level_ATTACHMENT-5-A"));
    assertThat(in("AN PO 12.4.2.4", AVSEC)).contains(exact("anx/pnt_12.4.2/pnt_12.4.2.4"));
  }

  @Test
  void findsAChapterOurEIdsKeepAsAPointBesideItsOwn() {
    var chapters = List.of("anx", "anx/pnt_3", "anx/pnt_3.0.1", "anx/pnt_6", "anx/pnt_6.8.1.6");

    assertThat(in("AN CHA 3 TXT", chapters)).contains(new Found(List.of("anx/pnt_3"), true));
    assertThat(in("AN CHA 6 PO 6.8.1.6", chapters)).contains(exact("anx/pnt_6.8.1.6"));
  }

  @Test
  void findsNoneForACodeThatItDoesNotKnow() {
    // The rule "no-guess" (catalogue/places.feature) has the real examples.
    assertThat(in("DU 1", EMIR)).isEmpty();
  }

  @Test
  void findsTheOccurrenceThatHoldsALetteredPointOrThePlaceItselfOrNone() {
    // The rule "a-place-the-text-does-not-have" (catalogue/places.feature) has the real examples.
    assertThat(new Location("AN I SCT I PTA (j)").around(EMIR))
        .contains(new Found(List.of("anx_I/level_1/pnt_I"), true));
    assertThat(new Location("AR 89 PA 1 ALN 1").around(EMIR)).contains(exact("art_89/par_1/sub_1"));
    assertThat(new Location("DU 1").around(EMIR)).isEmpty();
  }

  @Test
  void dropsTheUrisOfCellarsCodes() {
    assertThat(
            Location.parse(
                "{AR|http://publications.europa.eu/resource/authority/fd_370/AR} 81"
                    + " {PA|http://publications.europa.eu/resource/authority/fd_370/PA} 3"))
        .isEqualTo(new Location("AR 81 PA 3"));
  }

  @Test
  void citesTheOccurrenceItNames() {
    assertThat(new Location("AN PO 1.2.3").place().words()).isEqualTo("point 1.2.3");
    assertThat(new Location("AN PO 8.1.2.3 PTA (c)").place().words()).isEqualTo("point 8.1.2.3(c)");
    assertThat(new Location("AR 89 PA 1 ALN 1").place().words()).isEqualTo("Article 89(1)");
    assertThat(new Location("AN PO 11.1.2 FR").place().words()).isEqualTo("point 11.1.2");
    assertThat(new Location("AN CHA 5 APP 5-A").place().words()).isEqualTo("Appendix 5-A");
    assertThat(new Location("AN CHA 6 PO 6.8.1.6").place().words()).isEqualTo("point 6.8.1.6");
    assertThat(new Location("AR 1 PA 4 PO (c) PT (v)").place().words())
        .isEqualTo("Article 1(4)(c)(v)");
  }

  @Test
  void citesTheOccurrencesThatHoldTheOccurrenceItNames() {
    assertThat(new Location("AN APP 4-C PTA (c) TIRE 6").place().words())
        .isEqualTo("Appendix 4-C, point (c), indent 6");
    assertThat(new Location("AN PO 1.2.6.2 PA 2").place().words())
        .isEqualTo("point 1.2.6.2, paragraph 2");
    assertThat(new Location("AN II PRT VI SCT 3.3").place().words())
        .isEqualTo("Annex II, Part VI, Section 3.3");
    assertThat(new Location("AN V PRT B PTA (C)").place().words())
        .isEqualTo("Annex V, Part B, point (C)");
    assertThat(new Location("CHA IXA").place().words()).isEqualTo("Chapter IXA");
    assertThat(new Location("CHA III AR 5").place().words()).isEqualTo("Article 5");
  }

  @Test
  void codesAPlaceSoThatItNamesThePlaceAgain() {
    for (var code :
        List.of(
            "AN PO 1.2.3",
            "AN PO 8.1.2.3 PTA (c)",
            "AR 89 PA 1",
            "AR 1 PA 4 PO (c) PT (v)",
            "AN APP 4-C PTA (c) TIRE 6",
            "AN PO 1.2.6.2 PA 2",
            "AN II PRT VI SCT 3.3",
            "AN V PRT B PTA (C)",
            "CHA IXA")) {
      var place = new Location(code).place();
      assertThat(Location.of(place).place()).as(code).isEqualTo(place);
    }
  }

  @Test
  void findsAPlaceAmongTheOccurrencesOfAText() {
    assertThat(Location.of(new Place("point 8.1.2.3(c)")).in(AVSEC))
        .contains(exact("anx/pnt_8.1.2.3/pnt_c"));
    assertThat(Location.of(new Place("Article 1(4)(c)(v)")).in(EMIR))
        .contains(exact("art_1/par_4/pnt_c/pnt_v"));
    assertThat(Location.of(new Place("Article 89(5a)")).in(EMIR)).contains(exact("art_89/par_5a"));
  }

  /** The eIds are from 2018/1862, 692/2008 and 923/2012. */
  static final List<String> OTHERS =
      List.of(
          "cpt_IXa",
          "art_2",
          "art_2/pnt_90",
          "art_2/pnt_90/pnt_a",
          "art_9",
          "art_9/unp_1",
          "art_9/unp_1/pnt_1",
          "art_9/unp_1/pnt_4",
          "anx",
          "anx/anx_I",
          "anx/anx_I/app_3",
          "anx/anx_I/app_3/pnt_3.2.15.1",
          "anx/anx_IIIA",
          "anx/anx_IIIA/pnt_2.1",
          "anx/level_1",
          "anx/level_SECTION-1",
          "anx/pnt_SERA.5005",
          "anx/pnt_SERA.5005/pnt_c",
          "anx/pnt_3",
          "anx/level_CHAPTER-3");

  /** The eIds are from 2016/399, which has more than one annex. */
  static final List<String> BORDERS = List.of("anx_III", "anx_III/level_PART-D", "anx_V");

  @Test
  void findsANumberThatCellarWritesInAnotherCase() {
    assertThat(in("CHA IXa", OTHERS)).contains(exact("cpt_IXa"));
    assertThat(in("CHA IXA", OTHERS)).contains(exact("cpt_IXa"));
    assertThat(in("AN IIIA PO 2.1", OTHERS)).contains(exact("anx/anx_IIIA/pnt_2.1"));
    assertThat(in("AN PO SERA.5005 PO C)", OTHERS)).contains(exact("anx/pnt_SERA.5005/pnt_c"));
  }

  @Test
  void findsAnAppendixOfAnAnnexOfTheAnnex() {
    assertThat(in("AN I APP 3 PO 3.2.15.1", OTHERS))
        .contains(exact("anx/anx_I/app_3/pnt_3.2.15.1"));
  }

  @Test
  void findsAPartAndASectionOfAnAnnexByItsNamedLevel() {
    assertThat(in("AN III P D", BORDERS)).contains(exact("anx_III/level_PART-D"));
    assertThat(in("AN III PRT D", BORDERS)).contains(exact("anx_III/level_PART-D"));
    assertThat(in("AN SECTION 1", OTHERS)).contains(exact("anx/level_SECTION-1"));
  }

  @Test
  void findsAChapterOfAnAnnexThatIsAPointBeforeALevelWithItsNumber() {
    assertThat(in("AN CHA 3", OTHERS)).contains(exact("anx/pnt_3"));
  }

  @Test
  void findsANumberedPointThatCellarCodesAsAParagraph() {
    assertThat(in("AR 2 PA 90 PO (a)", OTHERS)).contains(exact("art_2/pnt_90/pnt_a"));
  }

  @Test
  void doesNotTakeAPointBelowAnUnnumberedParagraphForAParagraph() {
    assertThat(in("AR 9 PA 1 PO 4", OTHERS))
        .contains(new Found(List.of("art_9/unp_1/pnt_4"), true));
  }

  @Test
  void leavesATableOrAFootnoteAtTheOccurrenceItIsIn() {
    assertThat(in("AN PO SERA.5005 TABL S5-1 FOOTNOTE 2", OTHERS))
        .contains(new Found(List.of("anx/pnt_SERA.5005"), true));
  }

  @Test
  void putsAParagraphOfAnArticleThatTheTextDoesNotHaveAtTheArticleWithItsNumberAlone() {
    // The rule "a-place-the-text-does-not-have" (catalogue/places.feature) has the real examples.
    assertThat(new Location("AR 89a PA 2").around(EMIR))
        .contains(new Found(List.of("art_89"), true));
    // No Article 90 is in the text, so Article 90a has no place.
    assertThat(new Location("AR 90a").around(EMIR)).isEmpty();
  }

  private static Optional<Found> in(String code, List<String> eIds) {
    return new Location(code).in(eIds);
  }

  private static Found exact(String eId) {
    return new Found(List.of(eId), false);
  }
}
