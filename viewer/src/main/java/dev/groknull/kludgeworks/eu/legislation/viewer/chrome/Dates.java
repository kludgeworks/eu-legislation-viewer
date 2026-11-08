// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.chrome;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How every page writes a date. The pages are in English. */
public final class Dates {
  private static final DateTimeFormatter WORDS =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

  private Dates() {}

  /** Returns the date in words. For example, "1 June 2017". */
  public static String inWords(LocalDate date) {
    return WORDS.format(date);
  }
}
