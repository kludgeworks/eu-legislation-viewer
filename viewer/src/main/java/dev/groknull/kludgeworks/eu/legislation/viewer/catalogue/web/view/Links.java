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
 * The addresses of the pages. The address of an act's page uses the ELI of the act, as {@code
 * VersionController} and {@code CompareController} answer them. The address of the home page can
 * include the results of a search, as {@code HomeController} answers it.
 */
public final class Links {
  private Links() {}

  /**
   * Returns the address of the page of {@code version} in {@code language}. The address contains
   * the first day of the version and the code of the language, as an ELI has them. For example,
   * {@code /eli/reg_impl/2015/1998/2026-03-22/eng}.
   */
  public static String read(Eli eli, Version version, Language language) {
    return read(eli, version.date(), language);
  }

  /**
   * Returns the address of an act on EUR-Lex, by its ELI. For example, {@code
   * https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj}.
   */
  public static String eurLex(Eli eli) {
    return "https://eur-lex.europa.eu/eli/" + eli.path() + "/oj";
  }

  /**
   * Returns the address on EUR-Lex of {@code each}, in {@code language}. {@code each} is an act
   * that {@code act} integrates, or {@code act} itself. The address uses the ELI of {@code each} if
   * the Publications Office has one. If not, the address uses its CELEX.
   */
  public static String amending(Act act, ActIdentifier each, Language language) {
    return act.publication(each)
        .map(Publication::eli)
        .map(eli -> eurLex(eli) + "/" + language.code().toLowerCase(Locale.ROOT))
        .orElseGet(() -> eurLex(each.celex(), language));
  }

  /**
   * Returns the address of a document on EUR-Lex, by its CELEX, in {@code language}. The document
   * can be an act or a consolidated version.
   */
  public static String eurLex(String celex, Language language) {
    return "https://eur-lex.europa.eu/legal-content/"
        + language.iso().toUpperCase(Locale.ROOT)
        + "/TXT/?uri=CELEX:"
        + celex;
  }

  /**
   * Returns the address of the act in {@code language}. The date form on the Reading card sends its
   * date to this address, in the query parameter {@code on}.
   */
  public static String act(Eli eli, Language language) {
    return "/eli/" + eli.path() + "/" + language.code().toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the address of the comparison of {@code from} with {@code to} in {@code language}. The
   * address uses the first days of the two versions. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&to=2026-10-01}.
   */
  public static String compare(Eli eli, Version from, Version to, Language language) {
    return compare(eli, language) + "?from=" + from.date() + "&to=" + to.date();
  }

  /**
   * Returns the address of the same comparison, limited to the changes of the act {@code only}. For
   * example, {@code …&act=32026R0449}. If {@code only} is null, the comparison shows the changes of
   * all acts.
   */
  public static String compare(
      Eli eli, Version from, Version to, Language language, @Nullable ActIdentifier only) {
    return compare(eli, from, to, language) + act(only);
  }

  /**
   * Returns the address of the units that are the same in both versions. A fold in the Synopsis
   * loads them from this address. The fold adds its eIds after the address. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare/same?from=2021-07-01&to=2021-12-31}.
   */
  public static String same(Eli eli, Version from, Version to, Language language) {
    return compare(eli, language) + "/same?from=" + from.date() + "&to=" + to.date();
  }

  /**
   * Returns the address of the same comparison, in {@code view}. For example, {@code …&view=whole}.
   * The address of the Changes view has no {@code view} parameter.
   */
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
   * Returns the address from which the page of a comparison loads the comparison of {@code from}
   * with {@code to}. The comparison is in {@code view}, for example its Changes or its Whole text.
   * It is limited to the changes of {@code only}, if any. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare/changes?from=2026-03-22&to=2026-10-01}.
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
   * Returns the address from which the page of a comparison loads the acts between {@code from} and
   * {@code to}, with their counts. The chip of {@code only}, if any, is on. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare/acts?from=…&to=…}.
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

  /**
   * Returns the address of the event stream of the preparation of the act in the language. {@code
   * PreparationController} answers it.
   */
  public static String prepare(Eli eli, Language language) {
    return act(eli, language) + "/prepare/events";
  }

  /**
   * Returns the address of the comparisons of the act in {@code language}. The date forms on the
   * From and To cards send their dates to this address.
   */
  public static String compare(Eli eli, Language language) {
    return act(eli, language) + "/compare";
  }

  /**
   * Returns the address of the page of the version that applies on {@code date}, in {@code
   * language}.
   */
  public static String read(Eli eli, LocalDate date, Language language) {
    return "/eli/" + eli.path() + "/" + date + "/" + language.code().toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the address of what {@code amending} changed in the act {@code eli}, in {@code
   * language}. This is the comparison of the version that first integrates {@code amending} with
   * the version before it. It shows only the changes of {@code amending}. {@code CompareController}
   * finds the two versions. For example, {@code
   * /eli/reg_impl/2015/1998/eng/compare?act=32026R0449}.
   */
  public static String changesBy(Eli eli, ActIdentifier amending, Language language) {
    return compare(eli, language) + "?" + act(amending).substring(1);
  }

  /**
   * Returns the address of the home page with the results of a search for {@code words}, of {@code
   * kind}, on page {@code page}. Pages start at 1. For example, {@code
   * /?q=aviation+security&type=regulation&page=2}. The address has no {@code type} parameter for
   * all kinds, and no {@code page} parameter for the first page.
   */
  public static String home(String words, Search.Kind kind, int page) {
    return "/?q="
        + URLEncoder.encode(words, StandardCharsets.UTF_8)
        + (kind == Search.Kind.ALL ? "" : "&type=" + kind.code())
        + (page == 1 ? "" : "&page=" + page);
  }
}
