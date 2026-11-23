// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.JANUARY;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.MARCH;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.OCTOBER;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.only;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.said;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Differ} from end to end: a text against itself, a preamble that only one text has,
 * and the comparison of the consolidations of 2015/1998 of 22 March and 1 October 2026. 2026/449
 * applies from 22 March. Its points on screening of in-flight supplies and airport supplies apply
 * from 1 October.
 *
 * <p>Each stage has its own tests: {@code PairingTest}, {@code ComparerTest}, {@code
 * ComparedOccurrenceTest}, {@code OccurrenceEditsTest} and {@code OrderingTest}. The edits have
 * {@code EditsTest}, {@code RunsTest} and {@code WordingTest}. The business rules are in
 * viewer/src/test/rules/comparison.
 */
class DifferTest {
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

  /**
   * {@code text} with a preamble of one recital before its content, as in the consolidation of
   * 2016.
   */
  private static Element withPreamble(Element text) {
    var recital =
        new Element(
            "occurrence",
            Map.of("eId", "pbl_1/rct_1", "kind", "recital"),
            List.of(new Element("p", Map.of(), List.of(), "Whereas:")),
            "");
    var preamble = new Element("preamble", Map.of("eId", "pbl_1"), List.of(recital), "");
    var children = new ArrayList<Element>();
    children.add(preamble);
    children.addAll(text.children());
    return new Element(text.name(), text.attributes(), children, "");
  }
}
