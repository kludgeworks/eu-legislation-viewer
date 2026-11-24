// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The wording that the catalogue's pages use for the dates of an act: from when it applies, and the
 * clause that sets it. The wording is in English. The wording of a version is in {@link
 * VersionWording}. A date is in words as every page writes it, {@link Dates}. How an act is cited
 * with its publication is the act's, {@link Act#citation}.
 */
public final class Wording {
  /**
   * Finds where the date in a title ends. Group 1 is the title up to its date, for example "…
   * 2015/1998 of 5 November 2015". Group 2 is the rest of the title. The spaces can be any
   * horizontal space ({@code \\h}), because the titles in Cellar have no-break spaces in dates. For
   * example, 18&nbsp;June 2012.
   */
  public static final Pattern DATED =
      Pattern.compile("(.+?\\hof\\h\\d{1,2}\\h\\p{Lu}\\p{Ll}+\\h\\d{4})\\h+(.+)");

  private Wording() {}

  /**
   * Returns the name of a language in English, because the pages are in English. For example,
   * "English" or "French".
   */
  public static String language(Language language) {
    return Locale.forLanguageTag(language.iso()).getDisplayLanguage(Locale.ENGLISH);
  }

  /** Returns why a text could not be made. For example, "the Publications Office didn't answer". */
  public static String failure(Failure failure) {
    return switch (failure) {
      case NO_FORMEX -> "the Publications Office has no Formex of it in this language";
      case OUT_OF_SCOPE -> "it isn't a legal act the viewer shows";
      case MISFILED -> "the source file under its number is another document's";
      case UNSUPPORTED -> "it uses something the viewer can't show yet";
      case UNAVAILABLE -> "the Publications Office didn't answer";
      case ERROR -> "something went wrong on our side";
    };
  }

  /**
   * Returns the provisions that set {@code dates}, cited with their act. {@code dates} are dates of
   * {@code act}. For example, "Article 2 of 2023/566". Returns an empty text if Cellar names no
   * provision.
   */
  public static String clause(List<ActDate> dates, ActIdentifier act) {
    var provisions = new LinkedHashSet<ActDate.Provision>();
    dates.forEach(date -> provisions.addAll(date.clause().provisions()));
    return provisions.isEmpty()
        ? ""
        : provisions.stream().map(ActDate.Provision::cited).collect(Collectors.joining(", "))
            + " of "
            + act.cited();
  }

  /**
   * Returns the dates of {@code act} with the clause that sets them, as its history shows them.
   * {@code applying} is when it applies, or null if Cellar has no dates of it. For example:
   *
   * <ul>
   *   <li>"From 1 June 2017 · Article 2 of 2017/815"
   *   <li>"Applies from more than one day: 1 August 2022 and 15 October 2026 · Article 2 of
   *       2022/1190"
   *   <li>"Article 6 of 2021/1133 sets the day from which it applies. The data does not give a date
   *       for that day."
   * </ul>
   */
  static String dates(ActIdentifier act, @Nullable Applying applying) {
    if (applying == null) {
      return "The data has no dates for this act.";
    }
    var clause = clause(applying.dates(), act);
    var cited = clause.isEmpty() ? "" : " · " + clause;
    return switch (applying) {
      case Applying.From from -> "From " + Dates.inWords(from.day()) + cited;
      case Applying.FromMoreThanOneDay parts ->
          "Applies from more than one day: " + days(parts, " and ") + cited;
      case Applying.SetBy _ ->
          clause.isEmpty()
              ? "The data does not give the day from which it applies."
              : clause
                  + " sets the day from which it applies. The data does not give a date for that"
                  + " day.";
      case Applying.InForce inForce ->
          "In force from "
              + Dates.inWords(inForce.day())
              + cited
              + ". The data does not give the day from which it applies.";
    };
  }

  /** Returns the days of {@code parts} in words, with {@code last} before the last day. */
  private static String days(Applying.FromMoreThanOneDay parts, String last) {
    var days = parts.starts().stream().map(part -> Dates.inWords(part.day())).toList();
    return days.size() < 2
        ? String.join("", days)
        : String.join(", ", days.subList(0, days.size() - 1)) + last + days.getLast();
  }

  /**
   * Returns why the page does not say if a change of {@code act} applies on the day. {@code
   * applying} is when the act applies, or null if Cellar has no dates of it. Returns null for an
   * act that applies from a day that Cellar does not know: each change has the state of the act,
   * and {@link #dates} says it one time for the act. For example:
   *
   * <ul>
   *   <li>"The data does not say from which of these days this change applies: 1 August 2022 or 15
   *       October 2026 · Article 2 of 2022/1190"
   *   <li>"The data does not give the day from which this applies."
   * </ul>
   */
  static @Nullable String unknown(ActIdentifier act, @Nullable Applying applying) {
    if (applying == null) {
      return "The data has no dates for this act.";
    }
    return switch (applying) {
      case Applying.FromMoreThanOneDay parts -> {
        var clause = clause(parts.dates(), act);
        yield "The data does not say from which of these days this change applies: "
            + days(parts, " or ")
            + (clause.isEmpty() ? "" : " · " + clause);
      }
      case Applying.SetBy _ -> null;
      case Applying.From _, Applying.InForce _ ->
          "The data does not give the day from which this applies.";
    };
  }

  /**
   * Returns the clause of {@code act} that sets its dates on {@code day}, with a separator before
   * it. For example, " · Article 99 of 2016/679". Returns an empty text if Cellar names none.
   */
  static String clauseOn(Act base, ActIdentifier act, LocalDate day) {
    List<ActDate> on =
        base.dates().datesOf(act).stream().filter(date -> day.equals(date.date())).toList();
    var clause = clause(on, act);
    return clause.isEmpty() ? "" : " · " + clause;
  }
}
