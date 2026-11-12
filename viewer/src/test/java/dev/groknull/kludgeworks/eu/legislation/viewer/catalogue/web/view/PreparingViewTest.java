// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress;
import org.junit.jupiter.api.Test;

/** Tests what the preparing page says while the act waits behind the versions of other acts. */
class PreparingViewTest {

  @Test
  void countsTheDatesOfTheAmendedProvisions() {
    assertThat(Progress.dates(386)).isEqualTo("386 dates, one for each amended provision");
    assertThat(Progress.dates(1)).isEqualTo("1 date, for the one amended provision");
    assertThat(Progress.dates(0)).isEqualTo("None: no act has amended its provisions");
  }

  @Test
  void saysPlainlyHowManyVersionsOfOtherActsAreAheadRoundingPastTen() {
    assertThat(Progress.waiting(43))
        .isEqualTo(
            "About 40 versions of other acts are ahead. Nothing's wrong; this one starts by"
                + " itself.");
    assertThat(Progress.waiting(3)).startsWith("3 versions of other acts are ahead.");
    assertThat(Progress.waiting(1)).startsWith("1 version of another act is ahead.");
    assertThat(Progress.waiting(0)).isEqualTo("Nothing's wrong; this one starts by itself.");
  }
}
