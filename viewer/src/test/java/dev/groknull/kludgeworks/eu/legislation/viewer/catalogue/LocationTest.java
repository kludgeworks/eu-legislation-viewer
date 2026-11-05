// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Cellar's locations of amendments found among a text's units: codes from its annotations on
 * 648/2012 (EMIR) and 2015/1998, eIds from their consolidations' texts.
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
  void findsWhatIsFinerThanOurUnitsInTheUnitItIsIn() {
    assertThat(in("AN PO 6.3.1.4 ALN 3", AVSEC))
        .contains(new Found(List.of("anx/pnt_6.3.1.4"), true));
    assertThat(in("AN PO 12.4.2.4 FR", AVSEC))
        .contains(new Found(List.of("anx/pnt_12.4.2/pnt_12.4.2.4"), true));
  }

  @Test
  void findsEachPointOfARange() {
    assertThat(in("AN PO 11.3.2-11.3.4", AVSEC))
        .contains(new Found(List.of("anx/pnt_11.3.2", "anx/pnt_11.3.3", "anx/pnt_11.3.4"), false));
  }

  @Test
  void findsNoneWhereACodeFindsNoUnitOrSeveral() {
    // a point the text doesn't have
    assertThat(in("AN I SCT I PTA (j)", EMIR)).isEmpty();
    // a point (i) in both sections, Cellar's section not found
    assertThat(in("AN I SCT III PTA (i)", EMIR)).isEmpty();
    assertThat(in("DU 1", EMIR)).isEmpty();
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
  void citesTheUnitItNames() {
    assertThat(new Location("AN PO 1.2.3").name()).isEqualTo("point 1.2.3");
    assertThat(new Location("AN PO 8.1.2.3 PTA (c)").name()).isEqualTo("point 8.1.2.3(c)");
    assertThat(new Location("AR 89 PA 1 ALN 1").name()).isEqualTo("Article 89(1)");
    assertThat(new Location("AN PO 11.1.2 FR").name()).isEqualTo("point 11.1.2");
    assertThat(new Location("AN CHA 5 APP 5-A").name()).isEqualTo("Appendix 5-A");
  }

  private static Optional<Found> in(String code, List<String> eIds) {
    return new Location(code).in(eIds);
  }

  private static Found exact(String eId) {
    return new Found(List.of(eId), false);
  }
}
