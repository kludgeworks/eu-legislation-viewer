// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The wording that the catalogue's pages use for versions and periods. The wording is in English. A
 * date is in words as every page writes it, {@link Dates}. How an act is cited with its publication
 * is the act's, {@link Act#citation}.
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
   * Returns what made a version.
   *
   * <ul>
   *   <li>For the original version, returns "the act as published".
   *   <li>For a version made by acts, names the acts and counts the corrigenda without names. For
   *       example, "made by 2019/103, 2019/1583 · also 1 corrigendum".
   *   <li>For a version made only by corrigenda, names the corrigenda.
   *   <li>If Cellar lists nothing, says that no amending act is listed.
   * </ul>
   */
  public static String made(Version version) {
    return made(version, version.integrates(), ActIdentifier::cited);
  }

  /**
   * Returns what made {@code version}, from {@code integrates}: the acts and corrigenda to name.
   */
  private static String made(
      Version version, List<ActIdentifier> integrates, Function<ActIdentifier, String> cite) {
    if (version.kind() == Version.Kind.ORIGINAL) {
      return "the act as published";
    }
    var acts = integrates.stream().filter(act -> !act.corrigendum()).toList();
    var corrigenda = integrates.size() - acts.size();
    if (acts.isEmpty()) {
      return corrigenda == 0
          ? "no amending act listed for this text"
          : "made by " + names(integrates, cite);
    }
    var also =
        switch (corrigenda) {
          case 0 -> "";
          case 1 -> " · also 1 corrigendum";
          default -> " · also " + corrigenda + " corrigenda";
        };
    return "made by " + names(acts, cite) + also;
  }

  /**
   * Returns what made a version, as its card says it. The text is the same as {@link
   * #made(Version)}, with three differences.
   *
   * <ul>
   *   <li>For the original version, it adds the Official Journal reference to "the act as
   *       published".
   *   <li>If the Publications Office lists no act, it names the acts that the text of the version
   *       names, from {@code named}.
   *   <li>It counts only the corrigenda published in {@code language}. A corrigendum is published
   *       only in the languages that it corrects.
   * </ul>
   */
  public static String made(
      Act act, Version version, List<ActIdentifier> named, Language language) {
    if (version.kind() == Version.Kind.ORIGINAL) {
      return "the act as published"
          + act.publication(act.id())
              .map(Publication::journal)
              .map(journal -> " · " + journal.reference())
              .orElse("");
    }
    if (version.integrates().isEmpty() && !named.isEmpty()) {
      return "made by " + names(named, act::cited);
    }
    return made(
        version,
        version.integrates().stream()
            .filter(
                each ->
                    !each.corrigendum()
                        || act.publication(each).flatMap(p -> p.title(language)).isPresent())
            .toList(),
        act::cited);
  }

  /**
   * Returns the acts that the card of a version names. The hover of its made line lists the
   * publications of these acts.
   *
   * <ul>
   *   <li>For the act as published, returns the act.
   *   <li>Else returns the acts that the version integrates.
   *   <li>If there are none, returns the acts that its text names.
   * </ul>
   */
  public static List<ActIdentifier> makers(Act act, Version version, List<ActIdentifier> named) {
    if (version.kind() == Version.Kind.ORIGINAL) {
      return List.of(act.id());
    }
    return version.integrates().isEmpty() ? named : version.integrates();
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

  private static String names(List<ActIdentifier> acts, Function<ActIdentifier, String> cite) {
    return acts.stream().map(cite).collect(Collectors.joining(", "));
  }

  /**
   * Returns which version gives the law on {@code day}, and how long it applies, for the line under
   * the day on a card. For example, "From the text of 31 December 2021 · applied until 28 February
   * 2022", or "A new text starts on this day · current text" on its first day.
   */
  public static String which(
      Act act, Version version, LocalDate day, LocalDate today, Language language) {
    var starts =
        day.equals(version.date())
            ? "A new text starts on this day"
            : "From the text of " + Dates.inWords(version.date());
    var until = until(act, version, today, language);
    return until.isEmpty() ? starts : starts + " · " + until;
  }

  /**
   * Returns how long {@code version} applies, after its first day, for the line under the date on a
   * card. For example, "applied until 28 February 2022 · current text".
   *
   * <ul>
   *   <li>For the act as published, which was in force before it applied, says both: "in force from
   *       15 November 2015 · applied 1 February 2016 to 28 February 2016".
   *   <li>For the latest version, says nothing of an end.
   *   <li>Adds "current text" or "next text", as {@link #period} does.
   * </ul>
   *
   * <p>Returns an empty text if there is nothing to say.
   */
  public static String until(Act act, Version version, LocalDate today, Language language) {
    var next = act.timeline().in(language).flatMap(t -> t.successor(version));
    if (act.appliesLater(version).isPresent()) {
      var period = period(act, version, today, language);
      return Character.toLowerCase(period.charAt(0)) + period.substring(1);
    }
    var until =
        next.map(
                after -> {
                  var last = after.date().minusDays(1);
                  return (last.isBefore(today) ? "applied until " : "applies until ")
                      + Dates.inWords(last);
                })
            .orElse("");
    var labelled = label(act, version, today, language, until);
    return labelled.startsWith(" · ") ? labelled.substring(3) : labelled;
  }

  /**
   * Returns the period of the version, with its label. For example:
   *
   * <ul>
   *   <li>"Applied 15 March 2021 to 30 December 2021"
   *   <li>"Applies from 22 March 2026 · current text"
   *   <li>For the act as published, the period starts at its entry into force: "In force from 15
   *       November 2015 · applied 1 February 2016 to 28 February 2016".
   *   <li>An act as published that applies in part says so, and from when it applies in full: "In
   *       force from 1 August 2024 · applies in part from 2 February 2025, in full from 2 August
   *       2026". An act with no date for the whole act names the clause that sets the other dates:
   *       "…; other parts from dates set in Article 141".
   * </ul>
   */
  public static String period(Act act, Version version, LocalDate today, Language language) {
    var timeline = act.timeline().in(language);
    var next = timeline.flatMap(t -> t.successor(version));
    var applies = act.appliesLater(version);
    if (applies.isPresent()) {
      // The act as published was in force before it applied.
      var from = applies.get();
      var partial = act.inPartFrom(from);
      var period =
          "In force from "
              + Dates.inWords(version.date())
              + " · "
              + next.map(
                      after -> {
                        var last = after.date().minusDays(1);
                        var verb = last.isBefore(today) ? "applied" : "applies";
                        if (!partial) {
                          return verb + " " + Dates.inWords(from) + " to " + Dates.inWords(last);
                        }
                        var full = act.inFullAfter(from).filter(day -> !day.isAfter(last));
                        return full.map(
                                day ->
                                    verb
                                        + " in part from "
                                        + Dates.inWords(from)
                                        + ", in full from "
                                        + Dates.inWords(day)
                                        + " to "
                                        + Dates.inWords(last))
                            .orElseGet(
                                () ->
                                    verb
                                        + " in part "
                                        + Dates.inWords(from)
                                        + " to "
                                        + Dates.inWords(last));
                      })
                  .orElseGet(
                      () ->
                          partial
                              ? "applies in part from " + Dates.inWords(from) + rest(act, from)
                              : "applies from " + Dates.inWords(from));
      return label(act, version, today, language, period);
    }
    var period =
        next.map(
                after -> {
                  var last = after.date().minusDays(1);
                  return (last.isBefore(today) ? "Applied " : "Applies ")
                      + Dates.inWords(version.date())
                      + " to "
                      + Dates.inWords(last);
                })
            .orElseGet(() -> "Applies from " + Dates.inWords(version.date()));
    return label(act, version, today, language, period);
  }

  /**
   * Returns what follows "applies in part from …" for the rest of the act. For example, ", in full
   * from 2 August 2026", or "; other parts from dates set in Article 141" for an act that has no
   * date for the whole act. Returns empty if Cellar says neither.
   */
  private static String rest(Act act, LocalDate from) {
    var full = act.inFullAfter(from);
    if (full.isPresent()) {
      return ", in full from " + Dates.inWords(full.get());
    }
    var provisions = act.unknownDays().stream().map(ActDate.Provision::cited).toList();
    return provisions.isEmpty()
        ? ""
        : "; other parts from dates set in " + String.join(", ", provisions);
  }

  /**
   * Returns the period with its labels.
   *
   * <ul>
   *   <li>"current text" is the version that applies today.
   *   <li>"next text" is the version after the current one, if its date is after today.
   * </ul>
   */
  private static String label(
      Act act, Version version, LocalDate today, Language language, String period) {
    var timeline = act.timeline().in(language);
    var current = timeline.flatMap(t -> t.applicableOn(today));
    var labelled = period;
    if (current.filter(version::equals).isPresent()) {
      labelled += " · current text";
    } else if (version.date().isAfter(today)
        && current
            .flatMap(c -> timeline.flatMap(t -> t.successor(c)))
            .filter(version::equals)
            .isPresent()) {
      labelled += " · next text";
    }
    return labelled;
  }
}
