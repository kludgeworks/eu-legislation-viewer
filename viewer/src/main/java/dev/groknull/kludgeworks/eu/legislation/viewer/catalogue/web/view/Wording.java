// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act.Unconsolidated;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The wording that the pages use for dates, acts and provisions. The wording is in English. */
public final class Wording {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

  /**
   * Matches the CELEX of a corrigendum. This is the CELEX of the act, then R and the number of the
   * corrigendum in parentheses. For example, 32019R0103R(01).
   */
  private static final Pattern CORRIGENDUM = Pattern.compile("(.+)R\\((\\d+)\\)");

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

  /** Returns the date in words. For example, "1 June 2017". */
  public static String date(LocalDate date) {
    return DATE.format(date);
  }

  /**
   * Returns how the pages cite an act.
   *
   * <ul>
   *   <li>An act is cited by its number, as the Official Journal cites it. For example, 2017/815.
   *   <li>A corrigendum is cited by its number and the act. For example, "corrigendum 1 to
   *       2019/103".
   *   <li>Anything else is cited by its CELEX.
   * </ul>
   */
  public static String act(ActIdentifier act) {
    var corrigendum = CORRIGENDUM.matcher(act.celex());
    if (corrigendum.matches()) {
      return "corrigendum "
          + Integer.parseInt(corrigendum.group(2))
          + " to "
          + act(new ActIdentifier(corrigendum.group(1)));
    }
    return act.number().orElseGet(act::celex);
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
    var acts = version.integrates().stream().filter(act -> !corrigendum(act)).toList();
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
   * Returns how {@code each} was published, as its hover says it. {@code each} is an act that
   * {@code act} integrates, or {@code act} itself. For example, "2026/449: Commission Implementing
   * Regulation (EU) 2026/449 of 27 February 2026 amending … · OJ L, 2026/449, 2.3.2026". If the act
   * has no title in {@code language}, the title is in English. If the Publications Office has
   * nothing, returns only the name of the act.
   */
  public static String published(Act act, ActIdentifier each, Language language) {
    var name = act(each);
    return act.publication(each)
        .map(
            publication -> {
              var title = publication.title(language).or(() -> publication.title(Language.ENG));
              var journal = publication.journal();
              return name
                  + title.map(t -> ": " + t).orElse("")
                  + (journal == null ? "" : " · " + journal.reference());
            })
        .orElse(name);
  }

  /** Returns the publications of {@code acts}, one line for each act, as a hover lists them. */
  public static String published(Act act, List<ActIdentifier> acts, Language language) {
    return acts.stream()
        .map(each -> published(act, each, language))
        .collect(Collectors.joining("\n"));
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
    return acts.stream().map(Wording::act).collect(Collectors.joining(", "));
  }

  /** Returns whether {@code act} is a corrigendum. For example, 32019R0103R(01) is one. */
  public static boolean corrigendum(ActIdentifier act) {
    return CORRIGENDUM.matcher(act.celex()).matches();
  }

  /**
   * Returns the text of a lag, for a date within the lag. For example, "On this date, 2017/815
   * already applied (from 1 June 2017, Article 2 of 2017/815). No consolidated version includes it
   * until 7 June 2017."
   */
  public static String lagOn(Unconsolidated lag) {
    var act = act(lag.act());
    var provisions = lag.appliesFrom().clause().provisions();
    var where = provisions.isEmpty() ? "" : ", " + provision(provisions.getFirst()) + " of " + act;
    return "On this date, "
        + act
        + " already applied (from "
        + date(lag.from())
        + where
        + "). No consolidated version includes it until "
        + date(lag.includedIn().date())
        + ".";
  }

  /**
   * Returns the text of a lag, for a version that the lag overlaps. For example, "From 1 June 2017
   * this text lags the law: 2017/815 already applied, and no version includes it until 7 June
   * 2017."
   */
  public static String lagOf(Unconsolidated lag) {
    return "From "
        + date(lag.from())
        + " this text lags the law: "
        + act(lag.act())
        + " already applied, and no version includes it until "
        + date(lag.includedIn().date())
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
        + act(uncertain.act())
        + ", in force from "
        + date(uncertain.inForce())
        + ", applies from a date "
        + (provisions.isEmpty() ? "it sets" : "its " + provision(provisions.getFirst()) + " sets")
        + ", and no version includes it until "
        + date(uncertain.includedIn().date())
        + ".";
  }

  /** Returns the name of a provision. For example, "Article 2", "Annex" or "Annex II". */
  public static String provision(Provision provision) {
    var type = provision.type().name();
    var name = type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT);
    return provision.number() == null ? name : name + " " + provision.number();
  }
}
