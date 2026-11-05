// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Run;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Run.Mark;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import org.junit.jupiter.api.Test;

/**
 * 2015/1998's consolidations of 1 January, 22 March and 1 October 2026 compared, as the content
 * module reads them back: 2026/449 applies from 22 March, its points on screening in-flight and
 * airport supplies from 1 October.
 */
class DifferTest {
  static final Element JANUARY = text("02015R1998-20260101");
  static final Element MARCH = text("02015R1998-20260322");
  static final Element OCTOBER = text("02015R1998-20261001");

  @Test
  void findsTheWordsAnActChangedInEachPointItChanged() {
    var differences = Differ.differences(MARCH, OCTOBER);
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .containsExactly(
            tuple("anx/pnt_8.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_8.1.2.3/pnt_g", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_c", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_e", Kind.CHANGED),
            tuple("anx/pnt_9.1.2.3/pnt_g", Kind.CHANGED));
    var c = differences.getFirst().blocks();
    assertThat(c).singleElement().satisfies(block -> assertThat(block.from()).isEqualTo(1));
    assertThat(c.getFirst().runs())
        .containsExactly(
            new Run(Mark.SAME, "x-ray equipment"),
            new Run(
                Mark.INSERTED,
                " applied in accordance with Attachment 6-J to the Annex to Implementing Decision"
                    + " C(2015) 8005"),
            new Run(Mark.SAME, ";"));
  }

  @Test
  void replacesAPhraseAsOneDeletionAndOneInsertion() {
    var g = Differ.differences(MARCH, OCTOBER).get(2).blocks().getFirst().runs();
    assertThat(g)
        .containsExactly(
            new Run(Mark.SAME, "EVD equipment applied in accordance with "),
            new Run(Mark.INSERTED, "Attachment 6-J to "),
            new Run(Mark.SAME, "the "),
            new Run(Mark.DELETED, "relevant provisions contained in Attachment 6-J"),
            new Run(Mark.INSERTED, "Annex to Implementing Decision C(2015) 8005,"),
            new Run(Mark.SAME, " and in combination with point (a)"),
            new Run(Mark.INSERTED, " of this point"),
            new Run(Mark.SAME, "."));
  }

  @Test
  void findsAUnitInsertedOrDeletedOnceWithItsOwnUnits() {
    var differences = Differ.differences(JANUARY, MARCH);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.INSERTED).hasSize(38);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.DELETED).hasSize(16);
    assertThat(differences).filteredOn(d -> d.kind() == Kind.CHANGED).hasSize(38);
    assertThat(differences)
        .extracting(Difference::eId, Difference::kind)
        .contains(tuple("anx/pnt_8.1.2.4", Kind.INSERTED), tuple("anx/pnt_9.1.3.6", Kind.DELETED))
        .containsSubsequence(
            tuple("anx/pnt_1.2.2.3", Kind.CHANGED),
            tuple("anx/pnt_1.2.2.3/pnt_a", Kind.INSERTED),
            tuple("anx/pnt_1.2.2.3/pnt_b", Kind.INSERTED),
            tuple("anx/pnt_1.2.2.3/pnt_c", Kind.INSERTED))
        .containsSubsequence(
            tuple("anx/pnt_9.1.3.5", Kind.CHANGED),
            tuple("anx/pnt_9.1.3.5/pnt_a", Kind.DELETED),
            tuple("anx/pnt_9.1.3.5/pnt_b", Kind.DELETED),
            tuple("anx/pnt_9.1.3.6", Kind.DELETED));
    assertThat(differences)
        .extracting(Difference::eId)
        .doesNotContain("anx/pnt_9.1.3.5/pnt_b/idt_1");
    assertThat(differences)
        .filteredOn(difference -> difference.kind() != Kind.CHANGED)
        .allSatisfy(difference -> assertThat(difference.blocks()).isEmpty());
  }

  @Test
  void findsNothingBetweenATextAndItself() {
    assertThat(Differ.differences(MARCH, MARCH)).isEmpty();
  }

  private static Element text(String celex) {
    return Decoder.element(Encoder.encode(LegalTexts.sample("06.02.1/" + celex + ".xml")));
  }
}
