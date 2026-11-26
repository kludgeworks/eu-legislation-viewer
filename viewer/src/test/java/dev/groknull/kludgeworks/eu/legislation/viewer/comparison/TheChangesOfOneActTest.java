// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests which differences of a comparison are at a place ({@link
 * TheChangesOfOneAct#differencesAtAPlace}). The eIds are from 2018/1862, as 2022/1190 changed it.
 */
class TheChangesOfOneActTest {
  private static final List<Difference> DIFFERENCES =
      List.of(
          difference("art_3/pnt_8", Kind.CHANGED),
          difference("art_20/par_1/sub_1", Kind.CHANGED),
          difference("art_20/par_1/sub_2", Kind.INSERTED),
          difference("art_20/par_2/pnt_b", Kind.CHANGED),
          difference("cpt_IXa", Kind.INSERTED));

  @Test
  void theDifferenceOfAnOccurrenceIsAtTheOccurrence() {
    assertThat(eIds(List.of("art_3/pnt_8"))).containsExactly("art_3/pnt_8");
  }

  @Test
  void theDifferencesOfTheOccurrencesInAnOccurrenceAreAtTheOccurrence() {
    assertThat(eIds(List.of("art_20/par_1")))
        .containsExactly("art_20/par_1/sub_1", "art_20/par_1/sub_2");
  }

  @Test
  void theDifferencesOfEachOccurrenceOfARangeAreAtTheRange() {
    assertThat(eIds(List.of("art_3/pnt_8", "art_20/par_2")))
        .containsExactly("art_3/pnt_8", "art_20/par_2/pnt_b");
  }

  @Test
  void anOccurrenceInAnOccurrenceInsertedWholeIsAtTheDifferenceOfThatOccurrence() {
    assertThat(eIds(List.of("cpt_IXa/art_37a/par_1"))).containsExactly("cpt_IXa");
  }

  @Test
  void anOccurrenceThatIsTheSameInBothTextsHasNoDifference() {
    assertThat(eIds(List.of("art_4"))).isEmpty();
    // art_2 is not art_20: an occurrence is in another by its path, not by its letters.
    assertThat(eIds(List.of("art_2"))).isEmpty();
  }

  private static List<String> eIds(List<String> place) {
    return TheChangesOfOneAct.differencesAtAPlace(DIFFERENCES, place).stream()
        .map(Difference::eId)
        .toList();
  }

  private static Difference difference(String eId, Kind kind) {
    return new Difference(
        eId,
        kind,
        new Sibling(eId, "level", ""),
        new Element("occurrence", Map.of("eId", eId), List.of(), ""),
        1,
        0,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }
}
