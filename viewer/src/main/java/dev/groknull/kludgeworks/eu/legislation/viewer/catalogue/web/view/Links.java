// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
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
 * VersionController} and {@code CompareController} answer them. The fragments of the compare page
 * that the comparison module fills are at the addresses of {@link #page}. The address of the home
 * page can include the results of a search, as {@code HomeController} answers it.
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
    return page(eli, from, to, language, null, null).href();
  }

  /**
   * Returns the compare page of the comparison of {@code from} with {@code to}, in {@code view},
   * limited to the changes of {@code only}, if any. Its fragments are the comparison module's: the
   * compare page loads its differences ({@link ComparePage#differences}) into its text slot. The
   * address of each fragment is the contract between the two modules, which {@link ComparePage}
   * writes: the comparison module answers them ({@code ComparisonViewsController} and {@code
   * DifferenceController}), and no Java ties the two modules together.
   */
  public static ComparePage page(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      @Nullable String view) {
    return page(eli, from.date(), to.date(), language, only, view);
  }

  /**
   * Returns the address of the comparison of the law on {@code from} with the law on {@code to}.
   * The address keeps the days: it is not moved to the first days of their versions.
   */
  public static String compare(Eli eli, LocalDate from, LocalDate to, Language language) {
    return page(eli, from, to, language, null, null).href();
  }

  /**
   * Returns the address of the comparison of the law on {@code from} with the law on {@code to}, in
   * {@code view}, limited to the changes of {@code only}, if any.
   */
  public static String compare(
      Eli eli,
      LocalDate from,
      LocalDate to,
      Language language,
      @Nullable ActIdentifier only,
      @Nullable String view) {
    return page(eli, from, to, language, only, view).href();
  }

  private static ComparePage page(
      Eli eli,
      LocalDate from,
      LocalDate to,
      Language language,
      @Nullable ActIdentifier only,
      @Nullable String view) {
    return new ComparePage(
        compare(eli, language), from, to, only == null ? null : only.celex(), view);
  }

  /**
   * Returns the address of the event stream of the preparation of the act in the language. {@code
   * PreparationController} answers it.
   */
  public static String prepare(Eli eli, Language language) {
    return act(eli, language) + "/prepare/events";
  }

  /**
   * Returns the address of the event stream that says when the lookup of the act ends. {@code
   * ActLookupController} answers it. For example, {@code /eli/reg/2005/396/lookup/events}.
   */
  public static String actLookup(Eli eli) {
    return "/eli/" + eli.path() + "/lookup/events";
  }

  /**
   * Returns the address of the event stream of the notices that the act changed, for its pages in
   * the language. {@code NoticeController} answers it.
   */
  public static String notices(Eli eli, Language language) {
    return act(eli, language) + "/notices";
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
   * Returns the address of the history of the act {@code eli} on {@code day}, in {@code language}.
   * The history lists its amending acts. Without a day, the history is for today. For example,
   * {@code /eli/reg/2018/1862/eng/history?on=2022-08-01}.
   */
  public static String history(Eli eli, @Nullable LocalDate day, Language language) {
    return act(eli, language) + "/history" + (day == null ? "" : "?on=" + day);
  }

  /**
   * Returns the address of the history of the act {@code eli} with two days, in {@code language}.
   * The history lists the amending acts between the days. For example, {@code
   * /eli/reg/2018/1862/eng/history?from=2021-08-03&to=2022-08-01}.
   */
  public static String history(Eli eli, LocalDate from, LocalDate to, Language language) {
    return act(eli, language) + "/history?from=" + from + "&to=" + to;
  }

  /**
   * Returns the address of what {@code amending} changed in the act {@code eli}, on {@code day}, in
   * {@code language}. Without a day, the page is for today. For example, {@code
   * /eli/reg/2018/1862/eng/history/32022R1190?on=2022-08-01}.
   */
  public static String amending(
      Eli eli, ActIdentifier amending, @Nullable LocalDate day, Language language) {
    return act(eli, language)
        + "/history/"
        + URLEncoder.encode(amending.celex(), StandardCharsets.UTF_8)
        + (day == null ? "" : "?on=" + day);
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
