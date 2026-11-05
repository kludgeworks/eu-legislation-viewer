// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class HomeViewTest {
  @Test
  void splitsATitleAfterItsDateWhoseSpacesDontBreak() {
    // as Cellar has it, a no-break space between the day and the month
    var match =
        new Search.Match(
            new ActIdentifier("32021D2147"),
            new Eli("dec/2021/2147"),
            new ResourceType("DEC"),
            "Commission Decision (EU) 2021/2147 of 3 December 2021 on the approval of civil"
                + " aviation security equipment with ‘EU Stamp’ marking (Text with EEA relevance)",
            true,
            List.of());

    var results =
        Objects.requireNonNull(
            HomeView.found(
                    "aviation security",
                    Search.Kind.ALL,
                    1,
                    20,
                    new Search.Found(List.of(match), false))
                .results());

    var row = results.rows().getFirst();
    assertThat(row.head()).isEqualTo("Commission Decision (EU) 2021/2147 of 3 December 2021");
    assertThat(row.rest())
        .isEqualTo(
            "on the approval of civil aviation security equipment with ‘EU Stamp’ marking (Text"
                + " with EEA relevance)");
    assertThat(row.kind()).isEqualTo("Decision");
    assertThat(row.href()).isEqualTo("/eli/dec/2021/2147/eng/compare");
  }
}
