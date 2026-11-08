// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparepage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The address of a compare page: the comparison of two versions of an act, in one language. It
 * gives the addresses of the page and of its parts.
 *
 * <p>For example, the page {@code
 * /eli/reg_impl/2015/1998/eng/compare?from=2026-03-22&to=2026-10-01} has its changes at {@code
 * /eli/reg_impl/2015/1998/eng/compare/changes?from=2026-03-22&to=2026-10-01}. A part finds its page
 * by dropping its own segment, {@link #ofPart}.
 *
 * <p>The constants are the paths and the names of the query's fields. They are constant
 * expressions, so that a controller can map them.
 *
 * @param path the path of the page. For example, {@code /eli/reg_impl/2015/1998/eng/compare}.
 * @param from the first day of the version at the From end
 * @param to the first day of the version at the To end
 * @param act the CELEX of the act that the comparison is limited to, or null for all acts
 * @param view the code of the view that the page shows, or null for the first view
 */
public record ComparePage(
    String path, LocalDate from, LocalDate to, @Nullable String act, @Nullable String view) {

  /** The path of the compare page, with the ELI of the act and the code of the language. */
  public static final String PATH = "/eli/{type}/{year}/{number}/{language}/compare";

  /** The segment of the part with the changes, in the view that {@link #VIEW} asks for. */
  public static final String CHANGES = "changes";

  /** The segment of the part with the acts between the two versions. */
  public static final String ACTS = "acts";

  /** The segment of the part with the units of a fold that are the same in both versions. */
  public static final String SAME = "same";

  /**
   * The segment of the part with one difference, or with some rows of one of its tables. A view
   * loads it when the reader opens a fold in the difference.
   */
  public static final String DIFFERENCE = "difference";

  /** The path of the part with the changes. */
  public static final String CHANGES_PATH = PATH + "/" + CHANGES;

  /** The path of the part with the acts. */
  public static final String ACTS_PATH = PATH + "/" + ACTS;

  /** The path of the part with the units of a fold. */
  public static final String SAME_PATH = PATH + "/" + SAME;

  /** The path of the part with one difference. */
  public static final String DIFFERENCE_PATH = PATH + "/" + DIFFERENCE;

  /** The field of the first day of the version at the From end. For example, 2026-03-22. */
  public static final String FROM = "from";

  /** The field of the first day of the version at the To end. */
  public static final String TO = "to";

  /** The field of the CELEX of the act that the comparison is limited to. */
  public static final String ACT = "act";

  /** The field of the code of the view. */
  public static final String VIEW = "view";

  /** The field that asks the acts for their counts, with the value {@code true}. */
  public static final String COUNT = "count";

  /** The field of the eId of the first unit of a fold. */
  public static final String FIRST = "first";

  /** The field of the eId of the last unit of a fold. */
  public static final String LAST = "last";

  /** The field of the eId of the unit of a difference. */
  public static final String EID = "eId";

  /** The field of the number of a table in a difference, from 0 in the order of the text. */
  public static final String TABLE = "table";

  /** The field of the rows of a table, by their numbers from 0. For example, {@code 10-40}. */
  public static final String ROWS = "rows";

  /**
   * Returns the page of the part at {@code part}, the path of the part's address. For example,
   * {@code /eli/reg_impl/2015/1998/eng/compare/changes} is a part of the page at {@code
   * /eli/reg_impl/2015/1998/eng/compare}.
   */
  public static ComparePage ofPart(
      String part, LocalDate from, LocalDate to, @Nullable String act, @Nullable String view) {
    return new ComparePage(part.substring(0, part.lastIndexOf('/')), from, to, act, view);
  }

  /**
   * Returns the address of the page. For example, {@code …/compare?from=…&to=…&act=32026R0449}. The
   * address has no {@code act} field for all acts, and no {@code view} field for the first view.
   */
  public String href() {
    return path + query(from, to) + act(act) + view(view);
  }

  /**
   * Returns the address of the same comparison, limited to the changes of {@code act}, in {@code
   * view}. If {@code act} is null, the comparison shows the changes of all acts. If {@code view} is
   * null, the page shows its first view.
   */
  public String href(@Nullable String act, @Nullable String view) {
    return new ComparePage(path, from, to, act, view).href();
  }

  /** Returns the address of the changes, with the page's query. */
  public String changes() {
    return part(CHANGES);
  }

  /**
   * Returns the address of the difference at the unit {@code eId}, with the page's query but not
   * its view. For example, {@code …/compare/difference?from=…&to=…&eId=anx_I}.
   */
  public String difference(String eId) {
    return path + "/" + DIFFERENCE + query(from, to) + act(act) + "&" + EID + "=" + encode(eId);
  }

  /** Returns the address of the acts between the two versions, with the page's query. */
  public String acts() {
    return part(ACTS);
  }

  /** Returns the address of the acts between the two versions, with their counts. */
  public String countedActs() {
    return acts() + "&" + COUNT + "=true";
  }

  /**
   * Returns the address of the units that are the same in the versions from {@code earlier} and
   * from {@code later}. A fold loads them from this address, followed by {@code &} and its {@link
   * #fold} query. For example, {@code /eli/reg_impl/2015/1998/eng/compare/same?from=…&to=…}.
   */
  public String same(LocalDate earlier, LocalDate later) {
    return path + "/" + SAME + query(earlier, later);
  }

  /**
   * Returns the query that asks for the units of a fold, from the unit {@code first} to the unit
   * {@code last}, by their eIds. For example, {@code first=anx%2Fpnt_a&last=anx%2Fpnt_b}.
   */
  public static String fold(String first, String last) {
    return FIRST + "=" + encode(first) + "&" + LAST + "=" + encode(last);
  }

  private String part(String segment) {
    return path + "/" + segment + query(from, to) + act(act) + view(view);
  }

  private static String query(LocalDate from, LocalDate to) {
    return "?" + FROM + "=" + from + "&" + TO + "=" + to;
  }

  private static String act(@Nullable String act) {
    return act == null ? "" : "&" + ACT + "=" + encode(act);
  }

  private static String view(@Nullable String view) {
    return view == null ? "" : "&" + VIEW + "=" + encode(view);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
