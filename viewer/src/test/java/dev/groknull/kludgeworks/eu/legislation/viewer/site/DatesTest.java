// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Tests how the pages write a date, with {@link Dates}. */
class DatesTest {

  @Test
  void writesADateInWords() {
    assertThat(Dates.inWords(LocalDate.of(2017, 6, 1))).isEqualTo("1 June 2017");
  }

  @Test
  void writesTheMonthAndTheYearOfARangeOnce() {
    assertThat(Dates.range(LocalDate.of(2017, 6, 1), LocalDate.of(2017, 6, 6)))
        .isEqualTo("1 to 6 June 2017");
    assertThat(Dates.range(LocalDate.of(2017, 5, 30), LocalDate.of(2017, 6, 6)))
        .isEqualTo("30 May to 6 June 2017");
    assertThat(Dates.range(LocalDate.of(2016, 12, 30), LocalDate.of(2017, 1, 6)))
        .isEqualTo("30 December 2016 to 6 January 2017");
  }
}
