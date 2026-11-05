// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The home page (Design: Home page): the Find an act box, with what the reader typed in it, and a
 * search's results, if there was one, or why there are none.
 *
 * @param words what the reader typed, as they typed it; empty before they have
 * @param results the search's results, none before a search
 * @param none why there are no results: no act has the CELEX or ELI typed; none otherwise
 */
public record HomeView(String words, @Nullable Results results, @Nullable String none) {
  /** The kinds of act, as each act's row names it, by their resource types' codes. */
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

  /**
   * Where a title's date ends: "… 2015/1998 of 5 November 2015", the rest after it. Its spaces any
   * (\\h): Cellar's titles have no-break ones in dates, 18&nbsp;June 2012.
   */
  private static final Pattern DATED =
      Pattern.compile("(.+?\\hof\\h\\d{1,2}\\h\\p{Lu}\\p{Ll}+\\h\\d{4})\\h+(.+)");

  /** The home page before a search. */
  public static HomeView empty() {
    return new HomeView("", null, null);
  }

  /** The home page saying why {@code words}, a CELEX or ELI, has no results: {@code none}. */
  public static HomeView none(String words, String none) {
    return new HomeView(words, null, none);
  }

  /**
   * The results of the search for {@code words}, of {@code kind}, its page {@code page} (from 1) of
   * {@code size}: what Cellar {@code found}.
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
    var title = DATED.matcher(match.title());
    var head = title.matches() ? title.group(1) : match.title();
    var rest = title.matches() ? title.group(2) : null;
    var goes = new ArrayList<Go>();
    if (match.amends().isEmpty() && match.eli() != null) {
      goes.add(
          new Go(
              match.inForce()
                  ? "Compare the latest version with the one before"
                  : "Compare its last version with the one before",
              Links.compare(match.eli(), Language.ENG)));
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

  /** What kind of act it is: an amending act, else its resource type's, else its CELEX's. */
  private static String kind(Search.Match match) {
    if (!match.amends().isEmpty()) {
      return "Amending act";
    }
    var type = match.type();
    var kind = type == null ? null : KINDS.get(type.code());
    if (kind != null) {
      return kind;
    }
    // the CELEX's type letter: 32015R1998
    return switch (match.id().celex().charAt(5)) {
      case 'R' -> "Regulation";
      case 'L' -> "Directive";
      case 'D' -> "Decision";
      default -> type == null ? "" : type.code();
    };
  }

  /** An act as a row's line cites it: 2015/1998, 185/2010; its CELEX if it has no number. */
  private static String cited(ActIdentifier id) {
    return id.number().orElseGet(id::celex);
  }

  /**
   * A search's results.
   *
   * @param words what was searched for
   * @param filters the kinds of act it can be limited to, the one it is on
   * @param rows the acts, the closest match first
   * @param first the first's place among all the results, from 1
   * @param last the last's
   * @param previous the page before, none on the first
   * @param next the page after, none on the last
   * @param size how many a page has
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
   * A filter's link.
   *
   * @param label what kind of act: Regulations
   * @param href the results of that kind
   * @param on whether the results are
   */
  public record Filter(String label, String href, boolean on) {}

  /**
   * An act among the results.
   *
   * @param celex its CELEX
   * @param kind what kind of act it is: Implementing Regulation, Amending act
   * @param head its title up to its date, the link
   * @param rest the rest of its title, none for a title without a date
   * @param goes where it goes, the first the title's link: none for an act without an ELI
   */
  public record Row(String celex, String kind, String head, @Nullable String rest, List<Go> goes) {
    public Row {
      goes = List.copyOf(goes);
    }

    /** Where the title goes: the first of goes; none without. */
    public @Nullable String href() {
      return goes.isEmpty() ? null : goes.getFirst().href();
    }
  }

  /**
   * Where a result goes.
   *
   * @param label what is there: Compare the latest version with the one before
   * @param href its address
   */
  public record Go(String label, String href) {}
}
