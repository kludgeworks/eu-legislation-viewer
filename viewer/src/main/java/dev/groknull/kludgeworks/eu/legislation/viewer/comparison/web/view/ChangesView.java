// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The Changes view of a comparison.
 *
 * <p>It shows only the provisions that differ, in the order of the later text. Each is numbered and
 * has a note of what happened to it. Between them, it shows each other unit of the later text once:
 *
 * <ul>
 *   <li>The opening of a unit that holds differences shows as context, where the differences enter
 *       it.
 *   <li>The units that are the same in both texts are folded, one fold for each run in one unit.
 *       For example, "Points (a) and (b) — no differences".
 * </ul>
 *
 * @param count how many differences there are, for example "6 differences"
 * @param breakdown how many differences there are of each kind, for example "2 inserted · 1 deleted
 *     · 3 amended". Null if there are no differences.
 * @param passages what the view shows, in order
 * @param numbers the numbers of the differences, for the pager
 */
public record ChangesView(
    String count, @Nullable String breakdown, List<Passage> passages, List<Integer> numbers) {

  /** A letter or digit in a num. A num that is only a mark, for example "—" or "•", has none. */
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  /**
   * The eId of a unit in a numbered point. For example, {@code anx/pnt_11.2.7/pnt_b} is in 11.2.7.
   */
  private static final Pattern IN_POINT = Pattern.compile("pnt_(\\d+(?:\\.\\d+)*)/[^/]+$");

  /**
   * A num with a word of its own, and what follows the word. For example, "Article 2", "CHAPTER
   * III" or "ANNEX I".
   */
  private static final Pattern NAMED = Pattern.compile("(\\p{L}{2,}) (.+)");

  public ChangesView {
    passages = List.copyOf(passages);
    numbers = List.copyOf(numbers);
  }

  /** Returns the Changes view of {@code comparison}. */
  public static ChangesView of(Comparison comparison) {
    return of(comparison, difference -> true, "");
  }

  /**
   * Returns the Changes view of {@code comparison} that shows only the differences that {@code
   * shown} keeps. For example, the differences of one act, under its filter.
   *
   * <ul>
   *   <li>The other differences are folded with the units around them that are the same in both
   *       texts. {@code others} tells what they are, for example "not changed by 2019/1583".
   *   <li>A unit is folded once, with its units.
   *   <li>A unit that holds a shown difference shows only its opening.
   *   <li>A deleted unit is left out, because the units of a fold are those of the later text.
   * </ul>
   */
  public static ChangesView of(Comparison comparison, Predicate<Difference> shown, String others) {
    var differences = comparison.differences().stream().filter(shown).toList();
    var passages = new ArrayList<Passage>();
    var numbers = new ArrayList<Integer>();
    var pending = new Pending(passages, others);
    var withoutPreamble = comparison.withoutPreamble();
    if (withoutPreamble != null) {
      passages.add(new NoPreamble(withoutPreamble));
    }
    // The units whose opening the view shows: a lead, or a shown difference.
    var opened = new HashSet<String>();
    for (var difference : comparison.differences()) {
      place(difference.context(), differences, pending, opened);
      if (!shown.test(difference)) {
        if (difference.kind() != Kind.DELETED && !holds(differences, difference.eId())) {
          pending.add(List.of(difference.unit()), difference.parent());
          pending.hidden++;
        }
        continue;
      }
      // If it is in a unit whose difference the filter leaves out, show the opening of that unit as
      // context.
      var parent = difference.parent();
      var lead = difference.lead();
      if (parent != null && lead != null && opened.add(parent)) {
        pending.fold();
        passages.add(new Lead(lead));
      }
      pending.fold();
      var number = numbers.size() + 1;
      numbers.add(number);
      passages.add(new Shown(number, difference, note(difference)));
      opened.add(difference.eId());
    }
    place(comparison.closing(), differences, pending, opened);
    pending.fold();
    return new ChangesView(count(differences.size()), breakdown(differences), passages, numbers);
  }

  /**
   * Places {@code context} in the view.
   *
   * <ul>
   *   <li>The opening of a unit that holds a difference in {@code shown} becomes a lead.
   *   <li>The opening of a unit that holds no shown difference is folded whole.
   *   <li>The units that are the same in both texts wait to be folded.
   * </ul>
   */
  private static void place(
      List<Context> context, List<Difference> shown, Pending pending, Set<String> opened) {
    for (var each : context) {
      var opening = each.opening();
      if (opening == null) {
        pending.add(each.units(), each.parent());
      } else if (holds(shown, each.units().getFirst().eId())) {
        pending.fold();
        pending.passages.add(new Lead(opening));
        opened.add(each.units().getFirst().eId());
      } else {
        // It holds differences, but the filter leaves out all of them.
        pending.add(each.units(), each.parent());
        pending.hidden++;
      }
    }
  }

  /** Returns whether one of {@code differences} is in the unit {@code eId}. */
  private static boolean holds(List<Difference> differences, String eId) {
    return differences.stream().anyMatch(d -> d.eId().startsWith(eId + "/"));
  }

  /**
   * The units that wait to be folded, in order, each once, and how many of them differ. The units
   * that are the same in both texts and in one unit get a fold of their own.
   */
  private static final class Pending {
    private final List<Passage> passages;
    private final String others;
    private final List<Sibling> units = new ArrayList<>();
    private final Set<String> eIds = new HashSet<>();
    private final Set<@Nullable String> parents = new HashSet<>();
    private int hidden;

    Pending(List<Passage> passages, String others) {
      this.passages = passages;
      this.others = others;
    }

    /**
     * Adds {@code siblings} in {@code parent}. It skips a sibling that is already waiting, or that
     * is in a unit that is waiting. If the waiting units are the same in both texts and in another
     * unit, it folds them first.
     */
    void add(List<Sibling> siblings, @Nullable String parent) {
      if (hidden == 0 && !parents.isEmpty() && !parents.contains(parent)) {
        fold();
      }
      for (var sibling : siblings) {
        if (!in(sibling.eId())) {
          units.add(sibling);
          eIds.add(sibling.eId());
          parents.add(parent);
        }
      }
    }

    /** Returns whether the unit {@code eId}, or a unit that it is in, is waiting. */
    private boolean in(String eId) {
      for (var at = eId; ; at = at.substring(0, at.lastIndexOf('/'))) {
        if (eIds.contains(at)) {
          return true;
        }
        if (at.indexOf('/') < 0) {
          return false;
        }
      }
    }

    /**
     * Adds the fold of the waiting units after the passages, and empties them. The note of the fold
     * is "no differences". If some of the units differ, the note is {@code others}, and the label
     * spans the units. For example, "Points 11.1.3 to 11.2.7(b) (21 provisions)". Does nothing if
     * no units are waiting.
     */
    void fold() {
      if (units.isEmpty()) {
        return;
      }
      passages.add(
          hidden == 0
              ? new Fold(ChangesView.fold(units), units, "no differences", true)
              : new Fold(span(units), units, others, false));
      units.clear();
      eIds.clear();
      parents.clear();
      hidden = 0;
    }
  }

  private static String count(int differences) {
    return differences == 0
        ? "No differences"
        : differences + (differences == 1 ? " difference" : " differences");
  }

  private static @Nullable String breakdown(List<Difference> differences) {
    if (differences.isEmpty()) {
      return null;
    }
    return String.join(
        " · ",
        kinds(differences, Kind.INSERTED) + " inserted",
        kinds(differences, Kind.DELETED) + " deleted",
        kinds(differences, Kind.CHANGED) + " amended");
  }

  private static long kinds(List<Difference> differences, Kind kind) {
    return differences.stream().filter(difference -> difference.kind() == kind).count();
  }

  /**
   * Returns what happened to {@code difference}. For example, "Inserted" or "Amended: 16 words
   * inserted, 7 deleted".
   */
  static String note(Difference difference) {
    return switch (difference.kind()) {
      case INSERTED -> "Inserted";
      case DELETED -> "Deleted";
      case CHANGED -> {
        var parts = new ArrayList<String>();
        if (difference.inserted() > 0) {
          parts.add(words(difference.inserted()) + " inserted");
        }
        if (difference.deleted() > 0) {
          parts.add(
              (parts.isEmpty() ? words(difference.deleted()) : difference.deleted()) + " deleted");
        }
        yield parts.isEmpty() ? "Amended" : "Amended: " + String.join(", ", parts);
      }
    };
  }

  private static String words(int words) {
    return words + (words == 1 ? " word" : " words");
  }

  /**
   * Returns the label of a fold of {@code siblings}, made from the nums and the kind of the units.
   *
   * <ul>
   *   <li>A num that has its own word keeps it as written, and the word appears once. For example,
   *       "Article 2", "Articles 2 to 5" or "ANNEXES I and II".
   *   <li>Other nums follow the noun of their kind. For example, "Point (a)", "Points (a) and (b)"
   *       or "Paragraphs 2. to 4.".
   *   <li>Units with no num, or with only a mark such as the dash of an indent, are counted. For
   *       example, "2 indents" or "3 subparagraphs".
   *   <li>If only some units have a num, the label names the first and the last of those and counts
   *       all the units. For example, "Points 1. to 8.1.2.2 (429 provisions)".
   *   <li>If the first and the last are of different kinds, each has its own noun. For example,
   *       "Attachment 1-A to point 6.1.2".
   * </ul>
   */
  static String fold(List<Sibling> siblings) {
    var first = siblings.getFirst();
    var last = siblings.getLast();
    var join = siblings.size() == 2 ? " and " : " to ";
    if (siblings.stream().allMatch(sibling -> !WORD.matcher(sibling.num()).find())) {
      var noun = noun(first.kind()).toLowerCase(Locale.ROOT);
      return siblings.size() + " " + (siblings.size() == 1 ? noun : plural(noun));
    }
    if (siblings.stream().anyMatch(sibling -> !WORD.matcher(sibling.num()).find())) {
      return span(siblings);
    }
    if (!sort(first).equals(sort(last))) {
      return mixed(first, last, null);
    }
    var named = siblings.stream().map(sibling -> NAMED.matcher(sibling.num())).toList();
    if (named.stream().allMatch(Matcher::matches)
        && named.stream().map(m -> m.group(1)).distinct().count() == 1) {
      var word = named.getFirst().group(1);
      return siblings.size() == 1
          ? first.num()
          : plural(word) + " " + named.getFirst().group(2) + join + named.getLast().group(2);
    }
    var noun = noun(first.kind());
    return siblings.size() == 1
        ? noun + " " + first.num()
        : plural(noun) + " " + first.num() + join + last.num();
  }

  /**
   * Returns the label of a fold of units that crosses units. It names the first and the last unit
   * with a num, and tells how many units there are. For example, "Points 11.1.3 to 11.2.7(b) (21
   * provisions)".
   *
   * <ul>
   *   <li>A point is cited with the point that it is in, because its num alone does not tell which
   *       point it is.
   *   <li>If no unit has a num, the label only counts the units. For example, "3 provisions".
   * </ul>
   */
  static String span(List<Sibling> units) {
    var numbered = units.stream().filter(unit -> WORD.matcher(unit.num()).find()).toList();
    var count = units.size() + (units.size() == 1 ? " provision" : " provisions");
    if (numbered.isEmpty()) {
      return count;
    }
    var first = numbered.getFirst();
    var last = numbered.getLast();
    if (!sort(first).equals(sort(last))) {
      return mixed(first, last, count);
    }
    var noun = noun(first.kind());
    var named = NAMED.matcher(first.num()).matches();
    if (numbered.size() == 1) {
      return (named ? cited(first) : noun + " " + cited(first)) + " (" + count + ")";
    }
    return (named ? cited(first) : plural(noun) + " " + cited(first))
        + " to "
        + cited(last)
        + " ("
        + count
        + ")";
  }

  /**
   * Returns the sort of {@code unit}, as a label names it. If its num has a word of its own, the
   * sort is that word, for example "ATTACHMENT" or "ANNEX". If not, it is the noun of its kind, for
   * example "Point".
   */
  private static String sort(Sibling unit) {
    var named = NAMED.matcher(unit.num());
    return named.matches() ? named.group(1).toUpperCase(Locale.ROOT) : noun(unit.kind());
  }

  /**
   * Returns the label of a run from {@code first} to {@code last}, which are units of different
   * sorts. Each is named by its own sort. {@code count}, if any, comes after. For example,
   * "Attachment 1-A to point 6.1.2 (371 provisions)".
   */
  private static String mixed(Sibling first, Sibling last, @Nullable String count) {
    var end = named(last);
    var label = named(first) + " to " + Character.toLowerCase(end.charAt(0)) + end.substring(1);
    return count == null ? label : label + " (" + count + ")";
  }

  /**
   * Returns {@code unit} named by its sort. For example, "Attachment 1-A", "Point 6.1.2" or "Point
   * 11.2.7(b)".
   */
  private static String named(Sibling unit) {
    var named = NAMED.matcher(unit.num());
    if (named.matches()) {
      var word = named.group(1);
      return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT) + " " + named.group(2);
    }
    return noun(unit.kind()) + " " + cited(unit);
  }

  /**
   * Returns the num of a unit. The num of a point follows the numbered point that it is in. For
   * example, "11.2.7(b)".
   */
  public static String cited(Sibling unit) {
    var in = IN_POINT.matcher(unit.eId());
    return unit.num().startsWith("(") && in.find() ? in.group(1) + unit.num() : unit.num();
  }

  /**
   * Returns the name of a unit of {@code kind}, for when its num does not tell it. For example,
   * "Point" or "Paragraph".
   */
  private static String noun(String kind) {
    return switch (kind) {
      case "paragraph" -> "Paragraph";
      case "subparagraph" -> "Subparagraph";
      case "recital" -> "Recital";
      case "citation" -> "Citation";
      case "indent" -> "Indent";
      case "definition" -> "Definition";
      case "article" -> "Article";
      case "annex" -> "Annex";
      default -> "Point";
    };
  }

  /**
   * Returns the plural of {@code noun}, in the same case. For example, "Points", "ANNEXES" or
   * "indents".
   */
  private static String plural(String noun) {
    var upper = noun.equals(noun.toUpperCase(Locale.ROOT));
    var lower = noun.toLowerCase(Locale.ROOT);
    var ending = lower.endsWith("x") || lower.endsWith("s") ? "es" : "s";
    return noun + (upper ? ending.toUpperCase(Locale.ROOT) : ending);
  }

  /** What the view shows, in order. */
  public sealed interface Passage {}

  /**
   * A note, where the preamble stands, that the preamble is not compared. The {@code side} text has
   * no preamble, as the Publications Office published it, although no act changed it.
   *
   * @param side the text without the preamble
   */
  public record NoPreamble(Side side) implements Passage {}

  /**
   * Units that stay folded until a reader asks for them. They are the same in both texts, or, under
   * an act filter, other acts changed them.
   *
   * @param label which units they are. For example, "Points (a) and (b)".
   * @param units its units, as the later text has them, in order, each with the units in it
   * @param note why they are folded. For example, "no differences" or "not changed by 2019/1583".
   * @param same whether they are the same in both texts. Then they are the neighbours of the
   *     difference after them, in the unit that they are in. False if some of them differ, by an
   *     act that the view leaves out.
   */
  public record Fold(String label, List<Sibling> units, String note, boolean same)
      implements Passage {
    public Fold {
      units = List.copyOf(units);
    }

    /**
     * Returns its units beside the differences around it. These are its first and last units, or
     * all its units if it has three or fewer.
     */
    public List<Sibling> beside() {
      return units.size() <= 3 ? units : List.of(units.getFirst(), units.getLast());
    }

    /**
     * Returns its units except the first and the last, folded as this fold is. The first and the
     * last are the neighbours of the differences around it. Returns null if it has three units or
     * fewer, because all of them are neighbours.
     */
    public @Nullable Fold between() {
      if (units.size() <= 3) {
        return null;
      }
      var inner = units.subList(1, units.size() - 1);
      return new Fold(same ? ChangesView.fold(inner) : span(inner), inner, note, same);
    }

    /**
     * Returns the query that asks for its units, from its first to its last, whatever their number.
     * For example, {@code first=anx%2Fpnt_8.1.2.3%2Fpnt_a&last=anx%2Fpnt_8.1.2.3%2Fpnt_b}.
     */
    public String query() {
      return ComparePage.fold(units.getFirst().eId(), units.getLast().eId());
    }
  }

  /**
   * The opening of the unit that the differences after it are in, shown as context.
   *
   * @param unit its num, heading and first block
   */
  public record Lead(Element unit) implements Passage {}

  /**
   * A difference, numbered.
   *
   * @param number its number, from 1, as its mark and the pager show it
   * @param difference the difference, with its unit as shown
   * @param note what happened to it
   */
  public record Shown(int number, Difference difference, String note) implements Passage {
    /** Returns its kind, as its CSS class has it: inserted, deleted or changed. */
    public String kind() {
      return difference.kind().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns its marked unit as shown, in the list that it is an item of in the text. Read lays it
     * out in the same way, with its number hanging beside its text. Returns the unit alone if it is
     * in no list.
     */
    public Element unit() {
      var list = difference.list();
      var shown = difference.shown();
      return list == null
          ? shown
          : new Element(list.name(), list.attributes(), List.of(shown), list.text());
    }

    /**
     * Returns its marked unit as shown, as {@link #unit} does, with the runs of rows in its tables
     * that are the same in both texts folded ({@link RowFolds}). The folds load their rows from
     * {@link #part}.
     */
    public Element folded(ComparePage page) {
      return RowFolds.of(unit(), part(page));
    }

    /**
     * Returns the address of the part with this difference alone, on the compare page {@code page}.
     * An annex inserted whole loads from it when the reader opens it.
     */
    public String part(ComparePage page) {
      return page.difference(difference.eId());
    }

    /**
     * Returns its unit as a ledger cites it, for example "1.2.3.8". A point follows the point that
     * it is in, for example "1.2.3.5(e)".
     */
    public String cited() {
      return ChangesView.cited(difference.unit());
    }

    /**
     * Returns whether it is an annex inserted whole. The views fold such an annex to its size, so
     * that a long comparison is not one annex long.
     */
    public boolean annexInserted() {
      return difference.annexInserted();
    }

    /**
     * Returns the size of its unit, as its fold says it. For example, "56 provisions · 1,234
     * words".
     */
    public String size() {
      var size = difference.size();
      return String.format(Locale.ROOT, "%,d provisions · %,d words", size.units(), size.words());
    }
  }
}
