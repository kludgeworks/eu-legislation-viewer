// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place.Level;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests how a place is named from its levels, and read back into levels. */
class PlaceTest {
  @Test
  void citesAParagraphAndAPointInBracketsAfterTheirArticle() {
    assertThat(
            Place.of(
                List.of(
                    new Level("Article", "1"),
                    new Level("paragraph", "4"),
                    new Level("point", "(c)"),
                    new Level("point", "(v)"))))
        .contains(new Place("Article 1(4)(c)(v)"));
  }

  @Test
  void leavesOutTheOneAnnexAndALevelWhoseNumberStartsTheNext() {
    assertThat(
            Place.of(
                List.of(
                    new Level("Annex", ""),
                    new Level("Chapter", "6"),
                    new Level("point", "6.8.1.6"))))
        .contains(new Place("point 6.8.1.6"));
    assertThat(
            Place.of(
                List.of(
                    new Level("Annex", ""),
                    new Level("point", "8.1.2.3"),
                    new Level("point", "(c)"))))
        .contains(new Place("point 8.1.2.3(c)"));
  }

  @Test
  void keepsTheLevelsThatHoldItAndLeavesOutASubparagraph() {
    assertThat(
            Place.of(
                List.of(
                    new Level("Annex", "II"),
                    new Level("Part", "VI"),
                    new Level("Section", "3.3"))))
        .contains(new Place("Annex II, Part VI, Section 3.3"));
    assertThat(
            Place.of(
                List.of(
                    new Level("Article", "89"),
                    new Level("paragraph", "1"),
                    new Level("subparagraph", "1"))))
        .contains(new Place("Article 89(1)"));
  }

  @Test
  void namesTheOneAnnexWhenNothingInItIsNamed() {
    assertThat(Place.of(List.of(new Level("Annex", "")))).contains(new Place("the annex"));
    assertThat(Place.of(List.of())).isEmpty();
  }

  @Test
  void readsTheLevelsOfAName() {
    assertThat(new Place("Article 1(4)(c)(v)").levels())
        .containsExactly(
            new Level("Article", "1"),
            new Level("paragraph", "4"),
            new Level("point", "(c)"),
            new Level("point", "(v)"));
    assertThat(new Place("Article 3(a)").levels())
        .containsExactly(new Level("Article", "3"), new Level("point", "(a)"));
    assertThat(new Place("point 8.1.2.3(c)").levels())
        .containsExactly(new Level("point", "8.1.2.3"), new Level("point", "(c)"));
    assertThat(new Place("Appendix 4-C, point (c), indent 6").levels())
        .containsExactly(
            new Level("Appendix", "4-C"), new Level("point", "(c)"), new Level("indent", "6"));
    assertThat(new Place("the annex").levels()).containsExactly(new Level("Annex", ""));
  }

  @Test
  void namesTheLevelsItReadsAsBefore() {
    for (var words :
        List.of(
            "Article 89(1)",
            "Article 1(4)(c)(v)",
            "point 8.1.2.3(c)",
            "Annex II, Part VI, Section 3.3",
            "Appendix 4-C, point (c), indent 6",
            "point 1.2.6.2, paragraph 2",
            "Chapter IXA")) {
      var place = new Place(words);
      assertThat(Place.of(place.levels())).as(words).contains(place);
    }
  }

  @Test
  void refusesWordsThatNameNoPlace() {
    assertThatThrownBy(() -> new Place("somewhere").levels())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
