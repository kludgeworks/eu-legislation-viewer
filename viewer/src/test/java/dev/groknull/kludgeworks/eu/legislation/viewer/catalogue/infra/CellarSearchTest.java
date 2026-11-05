// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search.Amends;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search.Found;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search.Match;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cellar's recorded answer to a search for aviation security, folded into a page of acts. */
class CellarSearchTest {
  private static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");

  @Test
  void ranksByRelevanceThenActsAmendingNoneThenActsInForceThenTheNewer() {
    var found = found();

    assertThat(found.acts()).hasSize(20);
    assertThat(found.more()).isTrue();
    assertThat(found.acts().stream().map(match -> match.id().celex()))
        .startsWith(
            "32012D0312",
            "32020R0111",
            "32021D2147",
            "32009R0272",
            "32003R1217",
            "32024D2109",
            "32015R1998")
        .doesNotContain("32012R1082");
  }

  @Test
  void readsAnActWithItsEliTypeAndTitle() {
    var avsec = match(AVSEC);

    assertThat(avsec)
        .isEqualTo(
            new Match(
                AVSEC,
                new Eli("reg_impl/2015/1998"),
                new ResourceType("REG_IMPL"),
                "Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015 laying down"
                    + " detailed measures for the implementation of the common basic standards on"
                    + " aviation security (Text with EEA relevance)",
                true,
                List.of()));
  }

  @Test
  void readsTheActsAnActAmendsWithTheirElis() {
    assertThat(match(new ActIdentifier("32020R0111")).amends())
        .containsExactly(new Amends(AVSEC, new Eli("reg_impl/2015/1998")));
  }

  @Test
  void quotesEachWordForTheFullTextIndex() {
    assertThat(CellarSearch.words(List.of("aviation", "security")))
        .isEqualTo("'aviation' AND 'security'");
  }

  private static Match match(ActIdentifier id) {
    return found().acts().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
  }

  private static Found found() {
    return CellarSearch.found(RecordedActs.answer("search-aviation-security.json"), 20);
  }
}
