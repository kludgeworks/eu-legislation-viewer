// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView.Chip;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView.View;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the chips of {@link CompareView} for the version of 31 December 2021 of 2015/1998, against
 * the version before it. The chips are 2019/103, 2019/1583, 2020/910 and corrigendum 1 to 2019/103.
 * The data is Cellar's answer, recorded.
 */
class CompareViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final Version TO = AVSEC.timeline().startingOn(LocalDate.of(2021, 12, 31)).orElseThrow();
  static final Version FROM = AVSEC.timeline().predecessor(TO).orElseThrow();
  static final ActIdentifier CORRIGENDUM = new ActIdentifier("32019R0103R(01)");

  @Test
  void showsACorrigendumDottedOnlyWhereItCorrectedTheText() {
    assertThat(CompareView.between(AVSEC, FROM, TO)).contains(CORRIGENDUM);

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

  private static Acts acts(Map<ActIdentifier, Integer> counts, @Nullable ActIdentifier only) {
    return Acts.of(AVSEC, FROM, TO, Language.ENG, only, View.CHANGES, counts, null);
  }
}
