// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests which occurrences a flat chapter of an annex holds by their numbers. */
class TextOccurrencesTest {

  @Test
  void putsThePointsAndTheAttachmentsOfAFlatChapterInIt() {
    // The annex of 2015/1998 keeps a chapter flat: point 3 is the heading only.
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_3.0.1")).isTrue();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_3.1.3/pnt_a")).isTrue();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/level_ATTACHMENT-3-B")).isTrue();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/level_ATTACHMENT-3-B/pnt_1")).isTrue();
  }

  @Test
  void putsNoOccurrenceOfADifferentNumberInAFlatChapter() {
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_30.1")).isFalse();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_4.1")).isFalse();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/level_ATTACHMENT-30-B")).isFalse();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/level_ATTACHMENT-6-F")).isFalse();
    // The occurrence itself, and the occurrences below it by their eIds, are not beside it.
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_3")).isFalse();
    assertThat(TextOccurrences.holds("anx/pnt_3", "anx/pnt_3/pnt_a")).isFalse();
    // An occurrence of a different annex, and an occurrence that is not a point.
    assertThat(TextOccurrences.holds("anx_I/pnt_3", "anx_II/pnt_3.1")).isFalse();
    assertThat(TextOccurrences.holds("art_3", "art_3.1")).isFalse();
  }
}
