// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The addresses of the pages. The address of an act's page uses the ELI of the act, as {@code
 * VersionController} and {@code CompareController} answer them. The parts of the compare page that
 * the comparison module fills are at the addresses of {@link #page}. The address of the home page
 * can include the results of a search, as {@code HomeController} answers it.
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
   * Returns the address of the same comparison, limited to the changes of the act {@code only}. For
   * example, {@code …&act=32026R0449}. If {@code only} is null, the comparison shows the changes of
   * all acts.
   */
  public static String compare(
      Eli eli, Version from, Version to, Language language, @Nullable ActIdentifier only) {
    return page(eli, from, to, language, only, null).href();
  }

  /**
   * Returns the address of the same comparison, in the comparison module's view {@code view}. For
   * example, {@code …&view=whole}. The catalogue does not know the views: it passes the code on as
   * the reader gave it. Without a view, the address has no {@code view} parameter, and the
   * comparison module shows its first view.
   */
  public static String compare(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      @Nullable String view) {
    return page(eli, from, to, language, only, view).href();
  }

  /**
   * Returns the compare page of the comparison of {@code from} with {@code to}, in {@code view},
   * limited to the changes of {@code only}, if any. Its parts are the comparison module's: the
   * compare page loads its changes ({@link ComparePage#changes}) into its text slot, and its acts
   * ({@link ComparePage#acts}) into its acts slot. The address of each part is the contract between
   * the two modules, which the {@code comparepage} module writes: the comparison module answers
   * them ({@code ChangesController}), and no Java ties the two modules together.
   */
  public static ComparePage page(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      @Nullable String view) {
    return new ComparePage(
        compare(eli, language), from.date(), to.date(), only == null ? null : only.celex(), view);
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
    return compare(eli, language)
        + "?"
        + ComparePage.ACT
        + "="
        + URLEncoder.encode(amending.celex(), StandardCharsets.UTF_8);
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
