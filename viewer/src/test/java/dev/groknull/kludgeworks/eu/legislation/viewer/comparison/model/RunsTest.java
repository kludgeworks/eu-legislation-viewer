// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.MARCH;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.OCTOBER;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.descendants;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Runs}: how the edits of a block are written. Each run of inserted or deleted words
 * is in {@code diff:ins} or {@code diff:del}, around text only, so that each element keeps its
 * parent. The block is point 8.1.2.3(c) of 2015/1998, of 22 March and 1 October 2026.
 */
class RunsTest {

  @Test
  void keepsTheTextsStructureAroundItsEdits() {
    var edited = Edits.between(p(MARCH), p(OCTOBER)).element();

    assertThat(edited.name()).isEqualTo("p");
    assertThat(edited.children()).extracting(Element::name).contains("t", "diff:ins");
    assertThat(descendants(edited))
        .filteredOn(element -> element.name().startsWith("diff:"))
        .isNotEmpty()
        .allSatisfy(
            edit -> assertThat(edit.children()).extracting(Element::name).containsOnly("t"));
  }

  private static Element p(Element text) {
    return Objects.requireNonNull(Occurrences.of(text).get("anx/pnt_8.1.2.3/pnt_c"))
        .blocks()
        .stream()
        .filter(block -> block.name().equals("p"))
        .findFirst()
        .orElseThrow();
  }
}
