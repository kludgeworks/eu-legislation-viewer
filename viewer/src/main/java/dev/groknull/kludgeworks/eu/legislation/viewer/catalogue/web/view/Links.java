// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Locale;

/**
 * The pages' addresses: an act's by its ELI, as VersionController and CompareController answer
 * them.
 */
public final class Links {
  private Links() {}

  /**
   * The page of {@code version} in {@code language}: /eli/reg_impl/2015/1998/2026-03-22/eng, the
   * version's first day and the language's code, as an ELI has them.
   */
  public static String read(Eli eli, Version version, Language language) {
    return read(eli, version.date(), language);
  }

  /** An act on EUR-Lex, by its ELI: https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj. */
  public static String eurLex(Eli eli) {
    return "https://eur-lex.europa.eu/eli/" + eli.path() + "/oj";
  }

  /**
   * A document on EUR-Lex, by its CELEX (an act's, a consolidated version's), in {@code language}.
   */
  public static String eurLex(String celex, Language language) {
    return "https://eur-lex.europa.eu/legal-content/"
        + language.iso().toUpperCase(Locale.ROOT)
        + "/TXT/?uri=CELEX:"
        + celex;
  }

  /** The act in {@code language}, as the Reading card's date form asks for it with on. */
  public static String act(Eli eli, Language language) {
    return "/eli/" + eli.path() + "/" + language.code().toLowerCase(Locale.ROOT);
  }

  /**
   * The comparison of {@code from} with {@code to} in {@code language}, by their first days:
   * /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&amp;to=2026-10-01.
   */
  public static String compare(Eli eli, Version from, Version to, Language language) {
    return compare(eli, language) + "?from=" + from.date() + "&to=" + to.date();
  }

  /** The act's comparisons in {@code language}, as the From and To cards' date forms ask. */
  public static String compare(Eli eli, Language language) {
    return act(eli, language) + "/compare";
  }

  /** The page of the version that applies on {@code date}, in {@code language}. */
  public static String read(Eli eli, LocalDate date, Language language) {
    return "/eli/" + eli.path() + "/" + date + "/" + language.code().toLowerCase(Locale.ROOT);
  }
}
