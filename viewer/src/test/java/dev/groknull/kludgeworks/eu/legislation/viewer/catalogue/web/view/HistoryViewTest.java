// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.DatedAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Tests the state line of an act itself on a day. The act is 185/2010, which 2015/1998 repealed:
 * Cellar gives 14 November 2015 as its end of validity.
 */
class HistoryViewTest {
  private static final Act REPEALED = RecordedActs.act(new ActIdentifier("32010R0185"));

  @Test
  void saysThatARepealedActIsNoLongerInForceAndWhichActRepealedIt() {
    assertThat(own(LocalDate.of(2020, 1, 1), false))
        .isEqualTo(
            "Reg. (EU) 185/2010 is no longer in force. Its end of validity is 14 November 2015."
                + " 2015/1998 repealed it.");
  }

  @Test
  void saysItInThePastTenseForADayInThePast() {
    assertThat(own(LocalDate.of(2016, 1, 1), true))
        .startsWith("Reg. (EU) 185/2010 was no longer in force.");
  }

  @Test
  void givesTheStateFromTheDatesOnTheLastDayOfValidity() {
    assertThat(own(LocalDate.of(2015, 11, 14), false)).startsWith("Reg. (EU) 185/2010 applies.");
  }

  private static String own(LocalDate day, boolean past) {
    return HistoryView.own(
        REPEALED, DatedAct.of(REPEALED, REPEALED.id()).state(REPEALED).on(day), past);
  }
}
