// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The home page. It has the "Find an act" box, with what the reader typed in it. After a search, it
 * also has the results of the search, or the reason why there are none.
 *
 * @param words what the reader typed, as they typed it. Empty before the reader types.
 * @param results the results of the search. Null before a search.
 * @param none why there are no results, for example because no act has the CELEX or ELI that the
 *     reader typed. Null otherwise.
 */
public record HomeView(String words, @Nullable Results results, @Nullable String none) {
  /**
   * The names of the kinds of act, as the row of each act shows them, by the codes of their
   * resource types.
   */
  private static final Map<String, String> KINDS =
      Map.ofEntries(
          Map.entry("REG", "Regulation"),
          Map.entry("REG_IMPL", "Implementing Regulation"),
          Map.entry("REG_DEL", "Delegated Regulation"),
          Map.entry("REG_FINANC", "Financial Regulation"),
          Map.entry("DIR", "Directive"),
          Map.entry("DIR_IMPL", "Implementing Directive"),
          Map.entry("DIR_DEL", "Delegated Directive"),
          Map.entry("DEC", "Decision"),
          Map.entry("DEC_IMPL", "Implementing Decision"),
          Map.entry("DEC_DEL", "Delegated Decision"));

  /** Returns the home page before a search. */
  public static HomeView empty() {
    return new HomeView("", null, null);
  }

  /**
   * Returns the home page that says why {@code words}, a CELEX or ELI, has no results. {@code none}
   * is the reason.
   */
  public static HomeView none(String words, String none) {
    return new HomeView(words, null, none);
  }

  /**
   * Returns the home page with the results of the search for {@code words}, of {@code kind}. It
   * shows page {@code page} of the results, with {@code size} results on each page. Pages start at
   * 1. {@code found} is what Cellar found.
   */
  public static HomeView found(
      String words, Search.Kind kind, int page, int size, Search.Found found) {
    var filters =
        Arrays.stream(Search.Kind.values())
            .map(each -> new Filter(each.label(), Links.home(words, each, 1), each == kind))
            .toList();
    var rows = found.acts().stream().map(HomeView::row).toList();
    var first = (page - 1) * size + 1;
    return new HomeView(
        words,
        new Results(
            words,
            filters,
            rows,
            first,
            first + rows.size() - 1,
            page > 1 ? Links.home(words, kind, page - 1) : null,
            found.more() ? Links.home(words, kind, page + 1) : null,
            size),
        null);
  }

  private static Row row(Search.Match match) {
    var title = Wording.DATED.matcher(match.title());
    var head = title.matches() ? title.group(1) : match.title();
    var rest = title.matches() ? title.group(2) : null;
    var goes = new ArrayList<Go>();
    if (match.amends().isEmpty() && match.eli() != null) {
      goes.add(new Go("Read it as it is today", Links.act(match.eli(), Language.ENG)));
    }
    for (var amended : match.amends()) {
      var eli = amended.eli();
      if (eli != null) {
        goes.add(
            new Go(
                "What it changed in " + cited(amended.id()),
                Links.changesBy(eli, match.id(), Language.ENG)));
      }
    }
    return new Row(match.id().celex(), kind(match), head, rest, goes);
  }

  /**
   * Returns the name of the kind of act.
   *
   * <ul>
   *   <li>If the act amends acts, the name is "Amending act".
   *   <li>If not, the name comes from its resource type.
   *   <li>If the resource type is not known, the name comes from its CELEX.
   * </ul>
   */
  private static String kind(Search.Match match) {
    if (!match.amends().isEmpty()) {
      return "Amending act";
    }
    var type = match.type();
    var kind = type == null ? null : KINDS.get(type.code());
    if (kind != null) {
      return kind;
    }
    // Use the type letter of the CELEX. For example, R in 32015R1998.
    return switch (match.id().celex().charAt(5)) {
      case 'R' -> "Regulation";
      case 'L' -> "Directive";
      case 'D' -> "Decision";
      default -> type == null ? "" : type.code();
    };
  }

  /**
   * Returns the act as a line of a row cites it. For example, 2015/1998 or 185/2010. If the act has
   * no number, returns its CELEX.
   */
  private static String cited(ActIdentifier id) {
    return id.number().orElseGet(id::celex);
  }

  /**
   * The results of a search.
   *
   * @param words what was searched for
   * @param filters the kinds of act that the search can be limited to. One of them is on.
   * @param rows the acts, the closest match first
   * @param first the position of the first row among all the results, from 1
   * @param last the position of the last row among all the results
   * @param previous the address of the page before. Null on the first page.
   * @param next the address of the page after. Null on the last page.
   * @param size how many results a page has
   */
  public record Results(
      String words,
      List<Filter> filters,
      List<Row> rows,
      int first,
      int last,
      @Nullable String previous,
      @Nullable String next,
      int size) {
    public Results {
      filters = List.copyOf(filters);
      rows = List.copyOf(rows);
    }
  }

  /**
   * The link of a filter.
   *
   * @param label the kind of act. For example, "Regulations".
   * @param href the address of the results of that kind
   * @param on whether the results are limited to that kind
   */
  public record Filter(String label, String href, boolean on) {}

  /**
   * An act among the results.
   *
   * @param celex the CELEX of the act
   * @param kind what kind of act it is. For example, "Implementing Regulation" or "Amending act".
   * @param head the title of the act up to its date. This part is the link.
   * @param rest the rest of the title. Null for a title without a date.
   * @param goes where the row links to. The first one is the link of the title. Empty for an act
   *     without an ELI.
   */
  public record Row(String celex, String kind, String head, @Nullable String rest, List<Go> goes) {
    public Row {
      goes = List.copyOf(goes);
    }

    /**
     * Returns the address of the title link, which is the first of {@code goes}. Null if there are
     * none.
     */
    public @Nullable String href() {
      return goes.isEmpty() ? null : goes.getFirst().href();
    }
  }

  /**
   * A place that a result links to.
   *
   * @param label what is there. For example, "Compare the latest version with the one before".
   * @param href the address
   */
  public record Go(String label, String href) {}
}
