// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MarksView.Mark;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Tests what the amendment marks of 2015/1998 in Read say and where they lead. The versions and
 * dates are Cellar's, recorded.
 */
class MarksViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void listsEachActUpToTheVersionReadWithTheStepThatMadeIt() {
    var reading = AVSEC.timeline().startingOn(LocalDate.of(2026, 10, 1)).orElseThrow();

    var marks = MarksView.of(AVSEC, reading, Language.ENG).marks();

    assertThat(marks).extracting(Mark::act).doesNotHaveDuplicates().contains("32026R0449");
    var first = marks.getFirst();
    assertThat(first.compare())
        .isEqualTo("/eli/reg_impl/2015/1998/eng/compare?from=2015-11-15&to=2016-02-29");
    var last = marks.stream().filter(mark -> mark.act().equals("32026R0449")).findFirst();
    assertThat(last)
        .get()
        .extracting(Mark::applies, Mark::compare)
        .containsExactly(
            "applies in stages from 22 March 2026 · Article 2 of 2026/449",
            "/eli/reg_impl/2015/1998/eng/compare?from=2026-01-01&to=2026-03-22");
  }

  @Test
  void leavesOutTheActsOfLaterVersions() {
    var reading = AVSEC.timeline().startingOn(LocalDate.of(2016, 2, 29)).orElseThrow();

    assertThat(MarksView.of(AVSEC, reading, Language.ENG).marks())
        .hasSize(AVSEC.timeline().versions().get(1).integrates().size());
  }
}
