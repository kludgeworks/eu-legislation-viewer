// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which acts a version includes, as 767/2008 has them. Its version of 29 December 2017 tags 16
 * occurrences with 2017/2226. Cellar lists only Corrigendum 5 for it, and lists 2017/2226 from the
 * version of 11 June 2019.
 */
class InclusionsTest {
  static final ActIdentifier VIS = new ActIdentifier("32008R0767");
  static final ActIdentifier EES = new ActIdentifier("32017R2226");
  static final ActIdentifier CORRIGENDUM = new ActIdentifier("32008R0767R(05)");
  static final ActIdentifier LATER = new ActIdentifier("32021R1134");
  static final Version OCTOBER_2013 = version("2013-10-18");
  static final Version DECEMBER_2017 = version("2017-12-29", CORRIGENDUM);
  static final Version JUNE_2019 = version("2019-06-11", EES);
  static final Version AUGUST_2021 = version("2021-08-03", LATER);
  static final Timeline TIMELINE =
      new Timeline(List.of(OCTOBER_2013, DECEMBER_2017, JUNE_2019, AUGUST_2021));

  @Test
  void aVersionIncludesTheActsThatCellarListsAndThoseItsTextMarksFirst() {
    var included =
        Inclusions.of(
            TIMELINE,
            Map.of(
                OCTOBER_2013, tags(),
                DECEMBER_2017, tags(EES),
                JUNE_2019, tags(EES),
                AUGUST_2021, tags(EES, LATER)));

    assertThat(included.of(DECEMBER_2017)).containsExactly(CORRIGENDUM, EES);
    assertThat(included.between(OCTOBER_2013, DECEMBER_2017)).containsExactly(CORRIGENDUM, EES);
    // Cellar's list still counts where it lists the act again.
    assertThat(included.of(JUNE_2019)).containsExactly(EES);
    assertThat(included.of(AUGUST_2021)).containsExactly(LATER);
  }

  @Test
  void aTextThatIsNotBuiltLeavesOnlyCellarsList() {
    var included = Inclusions.of(TIMELINE, Map.of(OCTOBER_2013, tags()));

    assertThat(included.of(DECEMBER_2017)).containsExactly(CORRIGENDUM);
    assertThat(included.between(OCTOBER_2013, DECEMBER_2017)).containsExactly(CORRIGENDUM);
    assertThat(Inclusions.listed(TIMELINE).between(OCTOBER_2013, AUGUST_2021))
        .containsExactly(CORRIGENDUM, EES, LATER);
  }

  @Test
  void anActThatAnEarlierVersionIncludesIsNotFirstMarkedLater() {
    // The text of 2013 is not built, but Cellar lists the act from it.
    var timeline =
        new Timeline(List.of(version("2013-10-18", EES), version("2017-12-29", CORRIGENDUM)));
    var later = timeline.versions().getLast();

    var included = Inclusions.of(timeline, Map.of(later, tags(EES)));

    assertThat(included.of(later)).containsExactly(CORRIGENDUM);
  }

  private static TextTags tags(ActIdentifier... acts) {
    var byAct = new java.util.HashMap<ActIdentifier, Set<String>>();
    for (var each : acts) {
      byAct.put(each, Set.of("art_10"));
    }
    return new TextTags(List.of("art_10"), byAct);
  }

  private static Version version(String date, ActIdentifier... integrates) {
    return new Version(
        Version.Kind.CONSOLIDATED,
        LocalDate.parse(date),
        "0" + VIS.celex().substring(1) + "-" + date.replace("-", ""),
        false,
        List.of(integrates),
        EnumSet.of(Language.ENG),
        Map.of());
  }
}
