// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place.Subdivision;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests how a place is named from its subdivisions, and read back into its subdivisions. */
class PlaceTest {
  @Test
  void citesAParagraphAndAPointInBracketsAfterTheirArticle() {
    assertThat(
            Place.of(
                List.of(
                    new Subdivision("Article", "1"),
                    new Subdivision("paragraph", "4"),
                    new Subdivision("point", "(c)"),
                    new Subdivision("point", "(v)"))))
        .contains(new Place("Article 1(4)(c)(v)"));
  }

  @Test
  void leavesOutTheOneAnnexAndALevelWhoseNumberStartsTheNext() {
    assertThat(
            Place.of(
                List.of(
                    new Subdivision("Annex", ""),
                    new Subdivision("Chapter", "6"),
                    new Subdivision("point", "6.8.1.6"))))
        .contains(new Place("point 6.8.1.6"));
    assertThat(
            Place.of(
                List.of(
                    new Subdivision("Annex", ""),
                    new Subdivision("point", "8.1.2.3"),
                    new Subdivision("point", "(c)"))))
        .contains(new Place("point 8.1.2.3(c)"));
  }

  @Test
  void keepsTheLevelsThatHoldItAndLeavesOutASubparagraph() {
    assertThat(
            Place.of(
                List.of(
                    new Subdivision("Annex", "II"),
                    new Subdivision("Part", "VI"),
                    new Subdivision("Section", "3.3"))))
        .contains(new Place("Annex II, Part VI, Section 3.3"));
    assertThat(
            Place.of(
                List.of(
                    new Subdivision("Article", "89"),
                    new Subdivision("paragraph", "1"),
                    new Subdivision("subparagraph", "1"))))
        .contains(new Place("Article 89(1)"));
  }

  @Test
  void namesTheOneAnnexWhenNothingInItIsNamed() {
    assertThat(Place.of(List.of(new Subdivision("Annex", "")))).contains(new Place("the annex"));
    assertThat(Place.of(List.of())).isEmpty();
  }

  @Test
  void readsTheLevelsOfAName() {
    assertThat(new Place("Article 1(4)(c)(v)").subdivisions())
        .containsExactly(
            new Subdivision("Article", "1"),
            new Subdivision("paragraph", "4"),
            new Subdivision("point", "(c)"),
            new Subdivision("point", "(v)"));
    assertThat(new Place("Article 3(a)").subdivisions())
        .containsExactly(new Subdivision("Article", "3"), new Subdivision("point", "(a)"));
    assertThat(new Place("point 8.1.2.3(c)").subdivisions())
        .containsExactly(new Subdivision("point", "8.1.2.3"), new Subdivision("point", "(c)"));
    assertThat(new Place("Appendix 4-C, point (c), indent 6").subdivisions())
        .containsExactly(
            new Subdivision("Appendix", "4-C"),
            new Subdivision("point", "(c)"),
            new Subdivision("indent", "6"));
    assertThat(new Place("the annex").subdivisions()).containsExactly(new Subdivision("Annex", ""));
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
      assertThat(Place.of(place.subdivisions())).as(words).contains(place);
    }
  }

  @Test
  void refusesWordsThatNameNoPlace() {
    assertThatThrownBy(() -> new Place("somewhere").subdivisions())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
