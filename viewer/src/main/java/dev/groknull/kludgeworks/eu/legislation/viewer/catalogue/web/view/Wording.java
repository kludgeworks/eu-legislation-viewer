// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act.Unconsolidated;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
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
