// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ActsView.Chip;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the chips of {@link ActsView} for the version of 31 December 2021 of 2015/1998, against the
 * version before it. The chips are 2019/103, 2019/1583, 2020/910 and corrigendum 1 to 2019/103. The
 * data is Cellar's answer, recorded.
 */
class ActsViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final Version TO = AVSEC.timeline().startingOn(LocalDate.of(2021, 12, 31)).orElseThrow();
  static final Version FROM = AVSEC.timeline().predecessor(TO).orElseThrow();
  static final ActIdentifier CORRIGENDUM = new ActIdentifier("32019R0103R(01)");
  static final String PAGE = "/eli/reg_impl/2015/1998/eng/compare";

  @Test
  void showsACorrigendumDottedOnlyWhereItCorrectedTheText() {
    assertThat(AVSEC.integratedBetween(FROM, TO)).contains(CORRIGENDUM);

    assertThat(acts(Map.of(), null).chips()).noneMatch(Chip::corrigendum);
    assertThat(acts(Map.of(CORRIGENDUM, 0), null).chips()).noneMatch(Chip::corrigendum);
    assertThat(acts(Map.of(CORRIGENDUM, 2), null).chips())
        .filteredOn(Chip::corrigendum)
        .extracting(Chip::label, Chip::count)
        .containsExactly(tuple("corrigendum 1 to 2019/103", 2));
    assertThat(acts(Map.of(), CORRIGENDUM).chips()).filteredOn(Chip::on).hasSize(1);
  }

  @Test
  void countsTheActsBetweenWithoutTheirCorrigenda() {
    assertThat(acts(Map.of(CORRIGENDUM, 2), null).head()).isEqualTo("3 acts between them");
  }

  @Test
  void linksEachChipToItsPageLimitedToItsActAndTheChipThatIsOnToAllActs() {
    var only = new ActIdentifier("32019R0103");
    assertThat(acts(Map.of(), only).chips())
        .extracting(Chip::label, Chip::href)
        .contains(
            tuple("2019/103", PAGE + "?from=" + FROM.date() + "&to=" + TO.date()),
            tuple(
                "2020/910",
                PAGE + "?from=" + FROM.date() + "&to=" + TO.date() + "&act=32020R0910"));
  }

  private static ActsView acts(Map<ActIdentifier, Integer> counts, @Nullable ActIdentifier only) {
    var page =
        ComparePage.ofPart(
            PAGE + "/acts", FROM.date(), TO.date(), only == null ? null : only.celex(), null);
    return ActsView.of(AVSEC, FROM, TO, Language.ENG, page, counts, null);
  }
}
