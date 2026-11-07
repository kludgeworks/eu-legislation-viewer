// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The pages' addresses: an act's by its ELI, as VersionController and CompareController answer
 * them; the home page's, with a search's results (HomeController).
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
   * An act {@code act} integrates (or {@code act} itself) on EUR-Lex, in {@code language}: by its
   * ELI where the Publications Office has one (Design D-42), by its CELEX where it hasn't.
   */
  public static String amending(Act act, ActIdentifier each, Language language) {
    return act.publication(each)
        .map(Publication::eli)
        .map(eli -> eurLex(eli) + "/" + language.code().toLowerCase(Locale.ROOT))
        .orElseGet(() -> eurLex(each.celex(), language));
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

  /**
   * The same, limited to the changes of {@code only} (Design: Acts between the two versions):
   * …&amp;act=32026R0449; all acts' for none.
   */
  public static String compare(
      Eli eli, Version from, Version to, Language language, @Nullable ActIdentifier only) {
    return compare(eli, from, to, language) + act(only);
  }

  /**
   * The units the same in both versions, as a Synopsis fold loads them, the fold's eIds after:
   * /eli/reg_impl/2015/1998/eng/compare/same?from=2021-07-01&amp;to=2021-12-31.
   */
  public static String same(Eli eli, Version from, Version to, Language language) {
    return compare(eli, language) + "/same?from=" + from.date() + "&to=" + to.date();
  }

  /** The same, in {@code view}: …&amp;view=whole; the Changes view without. */
  public static String compare(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      CompareView.View view) {
    return compare(eli, from, to, language, only) + view(view);
  }

  /**
   * The comparison of {@code from} with {@code to} in {@code view} (its Changes, its Whole text),
   * as its page loads it, limited to the changes of {@code only}, if any:
   * /eli/reg_impl/2015/1998/eng/compare/changes?from=2026-03-22&amp;to=2026-10-01.
   */
  public static String changes(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      CompareView.View view) {
    return compare(eli, language)
        + "/changes?from="
        + from.date()
        + "&to="
        + to.date()
        + act(only)
        + view(view);
  }

  /**
   * The acts between {@code from} and {@code to}, counted, as the comparison's page loads them,
   * {@code only}'s chip on, if any: /eli/reg_impl/2015/1998/eng/compare/acts?from=…&amp;to=….
   */
  public static String acts(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      CompareView.View view) {
    return compare(eli, language)
        + "/acts?from="
        + from.date()
        + "&to="
        + to.date()
        + act(only)
        + view(view);
  }

  private static String view(CompareView.View view) {
    return view == CompareView.View.CHANGES ? "" : "&view=" + view.code();
  }

  private static String act(@Nullable ActIdentifier only) {
    return only == null ? "" : "&act=" + URLEncoder.encode(only.celex(), StandardCharsets.UTF_8);
  }

  /** The stream of the act's preparation in the language (PreparationController). */
  public static String prepare(Eli eli, Language language) {
    return act(eli, language) + "/prepare/events";
  }

  /** The act's comparisons in {@code language}, as the From and To cards' date forms ask. */
  public static String compare(Eli eli, Language language) {
    return act(eli, language) + "/compare";
  }

  /** The page of the version that applies on {@code date}, in {@code language}. */
  public static String read(Eli eli, LocalDate date, Language language) {
    return "/eli/" + eli.path() + "/" + date + "/" + language.code().toLowerCase(Locale.ROOT);
  }

  /**
   * What {@code amending} changed in the act {@code eli}, in {@code language}: the comparison of
   * the version that first integrates it with the one before, its changes alone, as
   * CompareController finds it: /eli/reg_impl/2015/1998/eng/compare?act=32026R0449.
   */
  public static String changesBy(Eli eli, ActIdentifier amending, Language language) {
    return compare(eli, language) + "?" + act(amending).substring(1);
  }

  /**
   * The home page with the results of a search for {@code words}, of {@code kind}, on page {@code
   * page} (from 1): /?q=aviation+security&amp;type=regulation&amp;page=2, all kinds' first page
   * without.
   */
  public static String home(String words, Search.Kind kind, int page) {
    return "/?q="
        + URLEncoder.encode(words, StandardCharsets.UTF_8)
        + (kind == Search.Kind.ALL ? "" : "&type=" + kind.code())
        + (page == 1 ? "" : "&page=" + page);
  }
}
