// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.articlesRenumbered;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.articlesWithPoint;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.compared;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.descendants;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.find;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.point;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.pointWithNum;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.said;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ComparedOccurrence}: when a changed occurrence shows whole, with the occurrences in
 * it, and how many words it inserts and deletes. The rule is same-acts-one-difference
 * (what-counts-as-a-difference.feature). These tests cover what its examples do not: an occurrence
 * in it that the same act deleted, and an occurrence in it that an act renumbered.
 */
class ComparedOccurrenceTest {

  @Test
  void showsAChangedOccurrenceWholeWithTheOccurrencesInItThatTheSameActsChangedOrDeleted() {
    var earlier =
        text(
            point(
                "1",
                "Old words",
                "A",
                point("1/a", "(a) old", "A"),
                point("1/b", "(b) gone", "A")));
    var later = text(point("1", "New words", "B", point("1/a", "(a) new", "B")));

    var one = find(compared(earlier, later), "1", false);

    assertThat(WhatCountsAsADifference.sameActsOneDifference(one)).isTrue();
    var shown = ShownOccurrence.of(one, true);
    assertThat(descendants(shown))
        .filteredOn(each -> each.name().equals("occurrence"))
        .extracting(each -> each.attribute("eId"))
        .containsExactly("1/a", "1/b");
    assertThat(said(shown)).contains("[+New]", "[+new]", "[-old]").containsPattern("\\[-[^]]*gone");
    // The deleted words are Old, old and the words of point (b).
    assertThat(one.deleted()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void countsTotalsOfWholeOccurrenceContainingRenumberedOccurrence() {
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

    var one = find(compared(earlier, later), "1", false);

    assertThat(WhatCountsAsADifference.sameActsOneDifference(one)).isTrue();
    assertThat(one.inserted()).isGreaterThan(0);
    assertThat(one.deleted()).isGreaterThan(0);
    assertThat(said(ShownOccurrence.of(one, true))).contains("[-Point a text]");
  }

  @Test
  void showsADeletedPointInTheRenumberedArticleThatTheSameActReplaced() {
    var article = find(compared(articlesWithPoint(), articlesRenumbered("ActX")), "art_6", false);

    assertThat(WhatCountsAsADifference.sameActsOneDifference(article)).isTrue();
    assertThat(said(ShownOccurrence.of(article, true)))
        .contains("[-Deleted point on inspection requirements.]");
  }

  @Test
  void showsTheArticleWithItsOwnContentOnlyWhereAnotherActRenumberedIt() {
    Element later = articlesRenumbered("ActY");
    var article = find(compared(articlesWithPoint(), later), "art_6", false);

    assertThat(WhatCountsAsADifference.sameActsOneDifference(article)).isFalse();
  }
}
