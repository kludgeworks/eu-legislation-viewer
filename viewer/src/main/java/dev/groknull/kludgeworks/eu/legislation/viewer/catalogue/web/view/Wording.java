// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act.Unconsolidated;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
    if (version.kind() == Version.Kind.ORIGINAL) {
      return "the act as published";
    }
    var acts = version.integrates().stream().filter(act -> !act.corrigendum()).toList();
    var corrigenda = version.integrates().size() - acts.size();
    if (acts.isEmpty()) {
      return corrigenda == 0
          ? "no amending act listed for this version"
          : "made by " + names(version.integrates());
    }
    var also =
        switch (corrigenda) {
          case 0 -> "";
          case 1 -> " · also 1 corrigendum";
          default -> " · also " + corrigenda + " corrigenda";
        };
    return "made by " + names(acts) + also;
  }

  /**
   * Returns what made a version, as its card says it. The text is the same as {@link
   * #made(Version)}, with two differences.
   *
   * <ul>
   *   <li>For the original version, it adds the Official Journal reference to "the act as
   *       published".
   *   <li>If the Publications Office lists no act, it names the acts that the text of the version
   *       names, from {@code named}. It says that they come from the consolidated text.
   * </ul>
   */
  public static String made(Act act, Version version, List<ActIdentifier> named) {
    if (version.kind() == Version.Kind.ORIGINAL) {
      return "the act as published"
          + act.publication(act.id())
              .map(Publication::journal)
              .map(journal -> " · " + journal.reference())
              .orElse("");
    }
    if (version.integrates().isEmpty() && !named.isEmpty()) {
      return "made by "
          + names(named)
          + " · named in the consolidated text; not in the Publications Office’s list";
    }
    return made(version);
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

  private static String names(List<ActIdentifier> acts) {
    return acts.stream().map(ActIdentifier::cited).collect(Collectors.joining(", "));
  }

  /**
   * Returns the text of a lag, for a date within the lag. For example, "On this date, 2017/815
   * already applied (from 1 June 2017, Article 2 of 2017/815). No consolidated version includes it
   * until 7 June 2017."
   */
  public static String lagOn(Unconsolidated lag) {
    var act = lag.act().cited();
    var provisions = lag.appliesFrom().clause().provisions();
    var where = provisions.isEmpty() ? "" : ", " + provisions.getFirst().cited() + " of " + act;
    return "On this date, "
        + act
        + " already applied (from "
        + Dates.inWords(lag.from())
        + where
        + "). No consolidated version includes it until "
        + Dates.inWords(lag.includedIn().date())
        + ".";
  }

  /**
   * Returns the text of a lag, for a version that the lag overlaps. For example, "From 1 June 2017
   * this text lags the law: 2017/815 already applied, and no version includes it until 7 June
   * 2017."
   */
  public static String lagOf(Unconsolidated lag) {
    return "From "
        + Dates.inWords(lag.from())
        + " this text lags the law: "
        + lag.act().cited()
        + " already applied, and no version includes it until "
        + Dates.inWords(lag.includedIn().date())
        + ".";
  }

  /**
   * Returns the text that says that the text of a version may lag the law. The card shows it in
   * grey. For example, "This text may lag the law: 2019/413, in force from 16 March 2019, applies
   * from a date its Article 3 sets, and no version includes it until 15 March 2021."
   */
  public static String mayLag(Act.Uncertain uncertain) {
    var provisions = uncertain.clause().provisions();
    return "This text may lag the law: "
        + uncertain.act().cited()
        + ", in force from "
        + Dates.inWords(uncertain.inForce())
        + ", applies from a date "
        + (provisions.isEmpty() ? "it sets" : "its " + provisions.getFirst().cited() + " sets")
        + ", and no version includes it until "
        + Dates.inWords(uncertain.includedIn().date())
        + ".";
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
  public static String period(Act act, Version version, LocalDate today) {
    var next = act.timeline().successor(version);
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
      return label(act, version, today, period);
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
    return label(act, version, today, period);
  }

  /**
   * Returns the period with its labels.
   *
   * <ul>
   *   <li>"current version" is the version that applies today.
   *   <li>"next version" is the version after the current one, if its date is after today.
   *   <li>"may still be revised" is an interim version.
   * </ul>
   */
  private static String label(Act act, Version version, LocalDate today, String period) {
    var current = act.versionOn(today);
    var labelled = period;
    if (current.filter(version::equals).isPresent()) {
      labelled += " · current version";
    } else if (version.date().isAfter(today)
        && current.flatMap(act.timeline()::successor).filter(version::equals).isPresent()) {
      labelled += " · next version";
    }
    return version.interim() ? labelled + " · may still be revised" : labelled;
  }
}
