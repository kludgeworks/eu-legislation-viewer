// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.block;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.formula;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.only;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Wording}: what a block says, as a comparison reads it. The rule
 * form-of-apostrophes-and-quotes (what-counts-as-a-difference.feature) has examples of a form blank
 * alone and at the end of a table cell. These tests cover a blank inside a field, and an ellipsis
 * joined to a word, which is not a blank. The rule formula-is-one-piece has an example of an
 * inserted formula. A test here covers a changed formula.
 */
class WordingTest {

  @Test
  void readsAFormBlankInsideAFieldThatOnlyOneTextPrintsAsNoChange() {
    // As point 2.17.1 of Appendix 3 to Annex I to 692/2008, of 1 January 2016.
    assertThat(Wording.key(block("p", "Mass in running order: … kg")))
        .isEqualTo(Wording.key(block("p", "Mass in running order: kg")));
  }

  @Test
  void readsAnEllipsisJoinedToAWordAsAChange() {
    assertThat(Wording.key(block("p", "as set out in Annex…")))
        .isNotEqualTo(Wording.key(block("p", "as set out in Annex")));
  }

  @Test
  void showsAChangedFormulaWholeAsDeletedThenInserted() {
    var from = formula("1");
    assertThat(Wording.tokens(from)).last().isEqualTo(Wording.of(only(from, "math")));

    var p = Edits.between(from, formula("2")).element();
    assertThat(p.children()).extracting(Element::name).containsExactly("t", "diff:del", "diff:ins");
    assertThat(p.children().get(1).children()).extracting(Element::name).containsExactly("math");
    assertThat(p.children().get(2).children()).extracting(Element::name).containsExactly("math");
  }
}
