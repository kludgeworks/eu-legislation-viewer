// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act.Unconsolidated;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The wording that the catalogue's pages use for versions, lags and periods. The wording is in
 * English. A date is in words as every page writes it, {@link Dates}. How an act is cited with its
 * publication is the act's, {@link Act#citation}.
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
          ? "no amending act listed for this version"
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

  private static String names(List<ActIdentifier> acts, Function<ActIdentifier, String> cite) {
    return acts.stream().map(cite).collect(Collectors.joining(", "));
  }

  /**
   * Returns the text of a lag, for {@code day} within the lag. For example, "On 3 June 2017,
   * 2017/815 already applied; this text doesn’t include it yet (it does from 7 June 2017)."
   */
  public static String lagOn(Unconsolidated lag, LocalDate day) {
    return "On "
        + Dates.inWords(day)
        + ", "
        + lag.act().cited()
        + " already applied; this text doesn’t include it yet (it does from "
        + Dates.inWords(lag.includedIn().date())
        + ").";
  }

  /**
   * Returns which version gives the law on {@code day}, and how long it applies, for the line under
   * the day on a card. For example, "From the version of 31 December 2021 · applied until 28
   * February 2022", or "A new version starts on this day · current version" on its first day.
   */
  public static String which(
      Act act, Version version, LocalDate day, LocalDate today, Language language) {
    var starts =
        day.equals(version.date())
            ? "A new version starts on this day"
            : "From the version of " + Dates.inWords(version.date());
    var until = until(act, version, today, language);
    return until.isEmpty() ? starts : starts + " · " + until;
  }

  /**
   * Returns how long {@code version} applies, after its first day, for the line under the date on a
   * card. For example, "applied until 28 February 2022 · current version".
   *
   * <ul>
   *   <li>For the act as published, which was in force before it applied, says both: "in force from
   *       15 November 2015 · applied 1 February 2016 to 28 February 2016".
   *   <li>For the latest version, says nothing of an end.
   *   <li>Adds "current version" or "next version", as {@link #period} does.
   * </ul>
   *
   * <p>Returns an empty text if there is nothing to say.
   */
  public static String until(Act act, Version version, LocalDate today, Language language) {
    var next = act.timeline().in(language).flatMap(t -> t.successor(version));
    if (applies(act, version).isPresent()) {
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
   * Returns the line that says that the act was in force on {@code day} but did not apply yet, if
   * so. {@code version} is the version on {@code day}. For example, "On 1 December 2015 the act was
   * in force but didn’t apply yet: it applies from 1 February 2016 · Article 2 of 2015/1998".
   */
  public static Optional<String> notApplying(Act act, Version version, LocalDate day) {
    return applies(act, version)
        .filter(day::isBefore)
        .map(
            from -> {
              var clause =
                  act.dates().stream()
                      .filter(date -> from.equals(date.date()))
                      .flatMap(date -> date.clause().provisions().stream())
                      .findFirst()
                      .map(provision -> " · " + provision.cited() + " of " + act.id().cited())
                      .orElse("");
              return "On "
                  + Dates.inWords(day)
                  + " the act was in force but didn’t apply yet: it applies from "
                  + Dates.inWords(from)
                  + clause;
            });
  }

  /**
   * Returns the day on which the act as published first applied, when that is after it entered into
   * force. {@code version} is the act as published. The day is the earliest date of application or
   * taking effect after the date of the version. Returns empty for a later version, or for an act
   * that applied when it entered into force.
   */
  public static Optional<LocalDate> applies(Act act, Version version) {
    if (version.kind() != Version.Kind.ORIGINAL) {
      return Optional.empty();
    }
    return act.dates().stream()
        .filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE)
        .map(ActDate::date)
        .filter(Objects::nonNull)
        .filter(day -> day.isAfter(version.date()))
        .min(Comparator.naturalOrder());
  }

  /**
   * Returns the period of the version, with its label. For example:
   *
   * <ul>
   *   <li>"Applied 15 March 2021 to 30 December 2021"
   *   <li>"Applies from 22 March 2026 · current version · may still be revised"
   *   <li>For the act as published, the period starts at its entry into force: "In force from 15
   *       November 2015 · applied 1 February 2016 to 28 February 2016".
   * </ul>
   */
  public static String period(Act act, Version version, LocalDate today, Language language) {
    var timeline = act.timeline().in(language);
    var next = timeline.flatMap(t -> t.successor(version));
    var applies = applies(act, version);
    if (applies.isPresent()) {
      // The act as published was in force before it applied.
      var from = applies.get();
      var partial =
          act.dates().stream().filter(date -> from.equals(date.date())).allMatch(ActDate::partial);
      var inPart = partial ? " in part" : "";
      var period =
          "In force from "
              + Dates.inWords(version.date())
              + " · "
              + next.map(
                      after -> {
                        var last = after.date().minusDays(1);
                        return (last.isBefore(today) ? "applied" : "applies")
                            + inPart
                            + " "
                            + Dates.inWords(from)
                            + " to "
                            + Dates.inWords(last);
                      })
                  .orElseGet(() -> "applies" + inPart + " from " + Dates.inWords(from));
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
   * Returns the period with its labels.
   *
   * <ul>
   *   <li>"current version" is the version that applies today.
   *   <li>"next version" is the version after the current one, if its date is after today.
   * </ul>
   */
  private static String label(
      Act act, Version version, LocalDate today, Language language, String period) {
    var timeline = act.timeline().in(language);
    var current = timeline.flatMap(t -> t.applicableOn(today));
    var labelled = period;
    if (current.filter(version::equals).isPresent()) {
      labelled += " · current version";
    } else if (version.date().isAfter(today)
        && current
            .flatMap(c -> timeline.flatMap(t -> t.successor(c)))
            .filter(version::equals)
            .isPresent()) {
      labelled += " · next version";
    }
    return labelled;
  }
}
