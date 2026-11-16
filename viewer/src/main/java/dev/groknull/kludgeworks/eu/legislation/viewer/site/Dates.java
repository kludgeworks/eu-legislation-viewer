// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.site;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How every page writes a date. The pages are in English. */
public final class Dates {
  private static final DateTimeFormatter WORDS =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
  private static final DateTimeFormatter DAY_MONTH =
      DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
  private static final DateTimeFormatter SHORT =
      DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

  private Dates() {}

  /** Returns the date in words. For example, "1 June 2017". */
  public static String inWords(LocalDate date) {
    return WORDS.format(date);
  }

  /**
   * Returns the days from {@code first} to {@code last} in words, without the month or the year
   * twice. For example, "1 to 6 June 2017", "30 May to 6 June 2017" or "30 December 2016 to 6
   * January 2017".
   */
  public static String range(LocalDate first, LocalDate last) {
    var start =
        first.getYear() != last.getYear()
            ? inWords(first)
            : first.getMonth() != last.getMonth()
                ? DAY_MONTH.format(first)
                : String.valueOf(first.getDayOfMonth());
    return start + " to " + inWords(last);
  }

  /**
   * Returns the date in words, with the month in short, as a page title has it. For example, "14
   * Feb 2022".
   */
  public static String inShort(LocalDate date) {
    return SHORT.format(date);
  }
}
