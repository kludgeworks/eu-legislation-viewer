// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The address of a compare page: the comparison of the law on two days, for an act in one language.
 * It gives the addresses of the page and of its parts.
 *
 * <p>The page has the two days that the reader gave. Its parts have the first days of the two
 * versions whose texts show the law on those days ({@code Act.textOn}), and the two days also.
 *
 * <p>For example, the page {@code
 * /eli/reg_impl/2015/1998/eng/compare?from=2016-02-29&to=2017-06-03} has its changes at {@code
 * /eli/reg_impl/2015/1998/eng/compare/changes?from=2016-02-29&to=2017-06-07&fromDay=2016-02-29&toDay=2017-06-03}.
 * A part finds its page by dropping its own segment, {@link #ofPart}, and by its days, {@link #on}.
 *
 * <p>The page is the catalogue's, and its parts are the comparison module's. The rule that ties a
 * part to its page is the contract between the two: a part's address is the page's address with a
 * segment added, and the page's query. The comparison module maps its controller to the constants
 * here, so the rule is written once.
 *
 * <p>The constants are the paths and the names of the query's fields. They are constant
 * expressions, so that a controller can map them.
 *
 * @param path the path of the page. For example, {@code /eli/reg_impl/2015/1998/eng/compare}.
 * @param from the first day of the version at the From end
 * @param to the first day of the version at the To end
 * @param act the CELEX of the act that the comparison is limited to, or null for all acts
 * @param view the code of the view that the page shows, or null for the first view
 * @param fromDay the earlier day of the page. Null where the page has the first days of the
 *     versions as its days.
 * @param toDay the later day of the page. Null as {@code fromDay}.
 */
public record ComparePage(
    String path,
    LocalDate from,
    LocalDate to,
    @Nullable String act,
    @Nullable String view,
    @Nullable LocalDate fromDay,
    @Nullable LocalDate toDay) {

  /** Makes the address of a page whose days are the first days of its versions. */
  public ComparePage(
      String path, LocalDate from, LocalDate to, @Nullable String act, @Nullable String view) {
    this(path, from, to, act, view, null, null);
  }

  /** The path of the compare page, with the ELI of the act and the code of the language. */
  public static final String PATH = "/eli/{type}/{year}/{number}/{language}/compare";

  /** The segment of the part with the changes, in the view that {@link #VIEW} asks for. */
  public static final String CHANGES = "changes";

  /** The segment of the part with the occurrences of a fold that are the same in both versions. */
  public static final String SAME = "same";

  /**
   * The segment of the part with one difference, or with some rows of one of its tables. A view
   * loads it when the reader opens a fold in the difference.
   */
  public static final String DIFFERENCE = "difference";

  /**
   * The segment of the part with the differences at one place: the occurrences that an amendment
   * names. The page of an amending act loads the wording of each of its changes from it.
   */
  public static final String PLACE = "place";

  /** The path of the part with the differences at one place. */
  public static final String PLACE_PATH = PATH + "/" + PLACE;

  /**
   * The segment of the part with the differences that one of two or more acts made, where the data
   * does not say which. The page of an amending act shows them in a separate list.
   */
  public static final String EITHER = "either";

  /** The path of the part with the differences that one of two or more acts made. */
  public static final String EITHER_PATH = PATH + "/" + EITHER;

  /** The path of the part with the changes. */
  public static final String CHANGES_PATH = PATH + "/" + CHANGES;

  /** The path of the part with the occurrences of a fold. */
  public static final String SAME_PATH = PATH + "/" + SAME;

  /** The path of the part with one difference. */
  public static final String DIFFERENCE_PATH = PATH + "/" + DIFFERENCE;

  /** The field of the first day of the version at the From end. For example, 2026-03-22. */
  public static final String FROM = "from";

  /** The field of the first day of the version at the To end. */
  public static final String TO = "to";

  /** The field of the CELEX of the act that the comparison is limited to. */
  public static final String ACT = "act";

  /** The field of the earlier day of the page, which only its parts have. */
  public static final String FROM_DAY = "fromDay";

  /** The field of the later day of the page, which only its parts have. */
  public static final String TO_DAY = "toDay";

  /**
   * The field of the eId of the provision that the page is limited to. Only the page has it. For
   * example, {@code art_6/par_2}.
   */
  public static final String OCCURRENCE = "occurrence";

  /** The field of the code of the view. */
  public static final String VIEW = "view";

  /**
   * The field that asks the differences at a place for the wording on each of the two days, side by
   * side, with the value {@code true}.
   */
  public static final String SIDES = "sides";

  /** The field of the eId of the first occurrence of a fold. */
  public static final String FIRST = "first";

  /** The field of the eId of the last occurrence of a fold. */
  public static final String LAST = "last";

  /** The field that asks a difference for its outline only, with the value {@code true}. */
  public static final String OUTLINE = "outline";

  /** The field of the eId of the occurrence of a difference. */
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
   * Returns the address of the same page, with the days {@code fromDay} and {@code toDay}. A null
   * day is the first day of the version at that end. A day that is the first day of its version is
   * not kept apart, so that the addresses of the parts stay short.
   */
  public ComparePage on(@Nullable LocalDate fromDay, @Nullable LocalDate toDay) {
    return new ComparePage(
        path,
        from,
        to,
        act,
        view,
        from.equals(fromDay) ? null : fromDay,
        to.equals(toDay) ? null : toDay);
  }

  /** Returns the earlier day of the page. */
  public LocalDate earlier() {
    return fromDay == null ? from : fromDay;
  }

  /** Returns the later day of the page. */
  public LocalDate later() {
    return toDay == null ? to : toDay;
  }

  /**
   * Returns the address of the page, with its days. For example, {@code …/compare?from=…&to=…}. The
   * address has no {@code act} field for all acts, and no {@code view} field for the first view.
   */
  public String href() {
    return path + query(earlier(), later()) + act(act) + view(view);
  }

  /**
   * Returns the address of the page, limited to the provision {@code occurrence}. For example,
   * {@code …/compare?from=…&to=…&occurrence=art_6%2Fpar_2}.
   */
  public String href(String occurrence) {
    return path + query(earlier(), later()) + "&" + OCCURRENCE + "=" + encode(occurrence);
  }

  /**
   * Returns the address of the same comparison, limited to the changes of {@code act}, in {@code
   * view}. If {@code act} is null, the comparison shows the changes of all acts. If {@code view} is
   * null, the page shows its first view.
   */
  public String href(@Nullable String act, @Nullable String view) {
    return new ComparePage(path, from, to, act, view, fromDay, toDay).href();
  }

  /** Returns the address of the changes, with the page's query. */
  public String changes() {
    return part(CHANGES);
  }

  /**
   * Returns the address of the difference at the occurrence {@code eId}, with the page's query but
   * not its view. For example, {@code …/compare/difference?from=…&to=…&eId=anx_I}.
   */
  public String difference(String eId) {
    return path + "/" + DIFFERENCE + query(from, to) + act(act) + "&" + EID + "=" + encode(eId);
  }

  /**
   * Returns the address of the differences that the page's act or another act of its version made,
   * where the data does not say which. For example, {@code
   * …/compare/either?from=…&to=…&act=32019R0413}.
   */
  public String either() {
    return path + "/" + EITHER + query(from, to) + act(act);
  }

  /**
   * Returns the address of the differences at the occurrences {@code eIds}, with the page's query
   * but not its view. For example, {@code
   * …/compare/place?from=…&to=…&act=32022R1190&eId=art_3%2Fpnt_8}.
   */
  public String place(List<String> eIds) {
    var occurrences = new StringBuilder();
    eIds.forEach(eId -> occurrences.append('&').append(EID).append('=').append(encode(eId)));
    return path + "/" + PLACE + query(from, to) + act(act) + occurrences;
  }

  /**
   * Returns the address of the wording of the occurrences {@code eIds} on each of the two days of
   * the page, side by side. It is the address of {@link #place}, with {@link #SIDES} and the days.
   */
  public String sides(List<String> eIds) {
    return place(eIds) + "&" + SIDES + "=true" + days();
  }

  /**
   * Returns the address of the outline of the difference at the occurrence {@code eId}: the
   * occurrences in it, without their text. An annex inserted whole shows it.
   */
  public String outline(String eId) {
    return difference(eId) + "&" + OUTLINE + "=true";
  }

  /**
   * Returns the address of the occurrence {@code eId} in Read, in the version at the To end. For
   * example, {@code /eli/reg_impl/2015/1998/2026-10-01/eng#anx_VIII} for the page {@code
   * /eli/reg_impl/2015/1998/eng/compare}.
   */
  public String read(String eId) {
    var act = path.substring(0, path.lastIndexOf('/'));
    var language = act.substring(act.lastIndexOf('/') + 1);
    return act.substring(0, act.lastIndexOf('/')) + "/" + to + "/" + language + "#" + encode(eId);
  }

  /**
   * Returns the address of the occurrences that are the same in the versions from {@code earlier}
   * and from {@code later}. A fold loads them from this address, followed by {@code &} and its
   * {@link #fold} query. For example, {@code /eli/reg_impl/2015/1998/eng/compare/same?from=…&to=…}.
   */
  public String same(LocalDate earlier, LocalDate later) {
    return path + "/" + SAME + query(earlier, later);
  }

  /**
   * Returns the query that asks for the occurrences of a fold, from the occurrence {@code first} to
   * the occurrence {@code last}, by their eIds. For example, {@code
   * first=anx%2Fpnt_a&last=anx%2Fpnt_b}.
   */
  public static String fold(String first, String last) {
    return FIRST + "=" + encode(first) + "&" + LAST + "=" + encode(last);
  }

  private String part(String segment) {
    return path + "/" + segment + query(from, to) + act(act) + view(view) + days();
  }

  private String days() {
    return (fromDay == null ? "" : "&" + FROM_DAY + "=" + fromDay)
        + (toDay == null ? "" : "&" + TO_DAY + "=" + toDay);
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
