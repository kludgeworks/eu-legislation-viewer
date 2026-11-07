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

/** How the pages write dates, acts and provisions: in English, as the design has them. */
public final class Wording {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

  /** A corrigendum's CELEX: the act's, then R and its number (32019R0103R(01)). */
  private static final Pattern CORRIGENDUM = Pattern.compile("(.+)R\\((\\d+)\\)");

  private Wording() {}

  /** A language by its name in English, as the pages are written: English, French. */
  public static String language(Language language) {
    return Locale.forLanguageTag(language.iso()).getDisplayLanguage(Locale.ENGLISH);
  }

  /** Why a text couldn't be made: the Publications Office didn't answer. */
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

  /** 1 June 2017. */
  public static String date(LocalDate date) {
    return DATE.format(date);
  }

  /**
   * An act by its number, as the Official Journal cites it (2017/815); a corrigendum by its number
   * and the act's (corrigendum 1 to 2019/103); anything else by its CELEX.
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
   * What made a version: the act as published (the original); made by 2019/103, 2019/1583 · also 1
   * corrigendum, the corrigenda counted, not named (Design: Corrigenda); or that Cellar lists
   * nothing.
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
   * How {@code each}, an act {@code act} integrates (or {@code act} itself), was published, as its
   * hover says (Design D-36, D-42, D-21): 2026/449: Commission Implementing Regulation (EU)
   * 2026/449 of 27 February 2026 amending … · OJ L, 2026/449, 2.3.2026; its title in English where
   * it has none in {@code language}; only its name where the Publications Office has nothing.
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

  /** {@code acts}' publications, a line each, as a hover lists them. */
  public static String published(Act act, List<ActIdentifier> acts, Language language) {
    return acts.stream()
        .map(each -> published(act, each, language))
        .collect(Collectors.joining("\n"));
  }

  /**
   * What made a version, as its card says it: as {@link #made(Version)}, the act as published with
   * its Official Journal reference; where the Publications Office lists no act, the acts the
   * version's own text names ({@code named}, Design D-19), saying so.
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
   * The acts a version's card names, whose publications its made line's hover lists: the act, for
   * the act as published; those the version integrates; else those its text names.
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

  /** Whether {@code act} is a corrigendum: 32019R0103R(01). */
  public static boolean corrigendum(ActIdentifier act) {
    return CORRIGENDUM.matcher(act.celex()).matches();
  }

  /**
   * A lag, on a date within it: On this date, 2017/815 already applied (from 1 June 2017, Article 2
   * of 2017/815). No consolidated version includes it until 7 June 2017.
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
   * A lag, for a version it overlaps: From 1 June 2017 this text lags the law: 2017/815 already
   * applied, and no version includes it until 7 June 2017.
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

  /** Article 2, Annex, Annex II. */
  public static String provision(Provision provision) {
    var type = provision.type().name();
    var name = type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT);
    return provision.number() == null ? name : name + " " + provision.number();
  }
}
