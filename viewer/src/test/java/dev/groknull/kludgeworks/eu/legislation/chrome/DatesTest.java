// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Tests how the pages write a date, with {@link Dates}. */
class DatesTest {

  @Test
  void writesADateInWords() {
    assertThat(Dates.inWords(LocalDate.of(2017, 6, 1))).isEqualTo("1 June 2017");
  }
}
