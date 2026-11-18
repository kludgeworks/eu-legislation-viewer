// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The day control of a page: the field that sets the day of the page, or its two days. The template
 * {@code catalogue/day.jte} writes it as a form with date boxes, and the element {@code
 * <day-picker>} makes one field and a calendar of it.
 *
 * <p>The calendar marks the days on which an amending act changes the act, {@link Act#changeDays}.
 * This view says, for each such day, what each act does on it:
 *
 * <ul>
 *   <li>"2021/255 applies from this day"
 *   <li>"A part of 2022/1190 applies from this day"
 *   <li>"2019/878 is in force from this day", where Cellar does not tell from when the act applies
 * </ul>
 *
 * @param on the day of the page, as a date box has it. For a page with two days, it is the second
 *     day.
 * @param from the first day of a page with two days. Null for a page with one day.
 * @param single the address that takes one day, in the parameter {@code on}
 * @param range the address that takes two days, in the parameters {@code from} and {@code to}. Null
 *     if the page takes one day only.
 * @param unit the eId of the provision that a page with two days is limited to, which the form
 *     keeps. Null for the whole act.
 * @param changes the days with a change, for the calendar. The days are in order, with a semicolon
 *     between them. A day is its ISO date, an equals sign, and what each act does on it, with a
 *     vertical bar between the acts.
 */
public record DayView(
    String on,
    @Nullable String from,
    String single,
    @Nullable String range,
    @Nullable String unit,
    String changes) {

  /**
   * Returns the day control of a page of {@code act} in {@code language}.
   *
   * @param day the day of the page, or its second day
   * @param first the first day of a page with two days, or null
   */
  static DayView of(
      Act act,
      LocalDate day,
      @Nullable LocalDate first,
      String single,
      @Nullable String range,
      @Nullable String unit,
      Language language) {
    return new DayView(
        day.toString(),
        first == null ? null : first.toString(),
        single,
        range,
        unit,
        act.changeDays(language).entrySet().stream()
            .map(
                each ->
                    each.getKey()
                        + "="
                        + each.getValue().stream()
                            .map(amending -> does(act, amending))
                            .collect(Collectors.joining("|")))
            .collect(Collectors.joining(";")));
  }

  /** Returns what {@code amending} does on one of its days of change, in words. */
  private static String does(Act act, ActIdentifier amending) {
    var cited = act.cited(amending);
    return act.applying(amending, Set.of())
        .map(
            applying ->
                switch (applying) {
                  case Applying.InParts _ -> "A part of " + cited + " applies from this day";
                  case Applying.InForce _ -> cited + " is in force from this day";
                  case Applying.From _, Applying.SetBy _ -> cited + " applies from this day";
                })
        .orElse(cited);
  }
}
