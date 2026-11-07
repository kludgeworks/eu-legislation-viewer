// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison.Side;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * A comparison's Changes view (Design: Mode: Compare, Changes): only the provisions that differ, in
 * the later text's order, each numbered, with a note of what happened to it; between them, the
 * later text's other units, each once: the opening of a unit that holds differences, as context,
 * where they enter it; the units the same in both, a run in one unit folded (Points (a) and (b) —
 * no differences).
 *
 * @param count how many differences: 6 differences
 * @param breakdown how many of each kind: 2 inserted · 1 deleted · 3 amended; none if there are
 *     none
 * @param passages what the view shows, in order
 * @param numbers the differences' numbers, for its pager
 */
public record ChangesView(
    String count, @Nullable String breakdown, List<Passage> passages, List<Integer> numbers) {

  /** A num's letter or digit: what a mark (—, •) hasn't. */
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

  /** A unit's eId in a numbered point: anx/pnt_11.2.7/pnt_b is in 11.2.7. */
  private static final Pattern IN_POINT = Pattern.compile("pnt_(\\d+(?:\\.\\d+)*)/[^/]+$");

  /** A num with a word of its own, and what follows it: Article 2, CHAPTER III, ANNEX I. */
  private static final Pattern NAMED = Pattern.compile("(\\p{L}{2,}) (.+)");

  public ChangesView {
    passages = List.copyOf(passages);
    numbers = List.copyOf(numbers);
  }

  /** {@code comparison}'s Changes view. */
  public static ChangesView of(Comparison comparison) {
    return of(comparison, difference -> true, "");
  }

  /**
   * {@code comparison}'s Changes view, showing only the differences {@code shown} keeps (an act's,
   * under its filter): the others are folded with the units the same in both around them, as {@code
   * others} says they are (not changed by 2019/1583), a unit once with its units; one that holds a
   * difference shown is left to its opening, and a deleted one out, as the fold's units are the
   * later text's.
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
    // the units whose opening the view shows: led, or a difference shown
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
      // in a unit whose difference the filter leaves out, its opening as context
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
   * {@code context} in the view: each opening of a unit holding a difference {@code shown} a lead,
   * one holding none folded whole; the units the same in both waiting to be folded.
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
        // it holds differences, all left out
        pending.add(each.units(), each.parent());
        pending.hidden++;
      }
    }
  }

  /** Whether one of {@code differences} is in the unit {@code eId}. */
  private static boolean holds(List<Difference> differences, String eId) {
    return differences.stream().anyMatch(d -> d.eId().startsWith(eId + "/"));
  }

  /**
   * Units waiting to be folded, in order, each once, and how many of them differ: those the same in
   * both, in one unit, a fold of their own.
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
     * {@code siblings}, in {@code parent}, but for those already in, or in one that is; those
     * before them folded first, where they are the same in both and in another unit.
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

    /** Whether the unit {@code eId} is waiting, or one it is in. */
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
     * Their fold, emptying them, after the passages: no differences, or as {@code others} says,
     * where some of them differ (Points 11.1.3 to 11.2.7(b) (21 provisions)); none if there are
     * none.
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

  /** What happened to {@code difference}: Inserted; Amended: 16 words inserted, 7 deleted. */
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
   * What a fold of {@code siblings} says, by their nums and kind: nums with a word of their own as
   * written, that word once (Article 2; Articles 2 to 5; ANNEXES I and II); other nums after their
   * kind's noun (Point (a); Points (a) and (b); Paragraphs 2. to 4.); units with no num, or only a
   * mark for one (an indent's dash), counted (2 indents; 3 subparagraphs); some of them, by the
   * first and last with one, all counted (Points 1. to 8.1.2.2 (429 provisions)); first and last of
   * different kinds, each by its own (Attachment 1-A to point 6.1.2).
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
   * What a fold of units across units says, by the first and last of them with a num and how many
   * there are: Points 11.1.3 to 11.2.7(b) (21 provisions); a point cited with the point it is in,
   * as its num alone doesn't say which; the units counted, where none has a num (3 provisions).
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
   * What sort of unit {@code unit} is, as a label names it: the word of a num with one of its own
   * (ATTACHMENT, ANNEX), otherwise its kind's noun (Point).
   */
  private static String sort(Sibling unit) {
    var named = NAMED.matcher(unit.num());
    return named.matches() ? named.group(1).toUpperCase(Locale.ROOT) : noun(unit.kind());
  }

  /**
   * A run from {@code first} to {@code last}, units of different sorts, each named by its own:
   * Attachment 1-A to point 6.1.2 (371 provisions); {@code count}, if any, after.
   */
  private static String mixed(Sibling first, Sibling last, @Nullable String count) {
    var end = named(last);
    var label = named(first) + " to " + Character.toLowerCase(end.charAt(0)) + end.substring(1);
    return count == null ? label : label + " (" + count + ")";
  }

  /** {@code unit} named by its sort: Attachment 1-A, Point 6.1.2, Point 11.2.7(b). */
  private static String named(Sibling unit) {
    var named = NAMED.matcher(unit.num());
    if (named.matches()) {
      var word = named.group(1);
      return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT) + " " + named.group(2);
    }
    return noun(unit.kind()) + " " + cited(unit);
  }

  /** A unit's num, a point's after the numbered point it is in: 11.2.7(b). */
  public static String cited(Sibling unit) {
    var in = IN_POINT.matcher(unit.eId());
    return unit.num().startsWith("(") && in.find() ? in.group(1) + unit.num() : unit.num();
  }

  /** What a unit of {@code kind} is called, where its num doesn't say: Point, Paragraph… */
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

  /** {@code noun}'s plural, in its case: Points, ANNEXES, indents. */
  private static String plural(String noun) {
    var upper = noun.equals(noun.toUpperCase(Locale.ROOT));
    var lower = noun.toLowerCase(Locale.ROOT);
    var ending = lower.endsWith("x") || lower.endsWith("s") ? "es" : "s";
    return noun + (upper ? ending.toUpperCase(Locale.ROOT) : ending);
  }

  /** What the view shows, in order. */
  public sealed interface Passage {}

  /**
   * Where the preamble stands, a note that it isn't compared: the {@code side} text hasn't one, as
   * the Publications Office published it, though no act changed it.
   *
   * @param side the text without it
   */
  public record NoPreamble(Side side) implements Passage {}

  /**
   * Units folded until asked for: the same in both texts, or, under an act filter, changed by
   * others.
   *
   * @param label which: Points (a) and (b)
   * @param units its units, as the later text has them, in order: each with the units in it
   * @param note why they are folded: no differences; not changed by 2019/1583
   * @param same whether they are the same in both texts, the neighbours of the difference after
   *     them in the unit they are in; not where some differ, by an act the view leaves out
   */
  public record Fold(String label, List<Sibling> units, String note, boolean same)
      implements Passage {
    public Fold {
      units = List.copyOf(units);
    }

    /** Its units beside the differences around it: its first and last, or all of three or fewer. */
    public List<Sibling> beside() {
      return units.size() <= 3 ? units : List.of(units.getFirst(), units.getLast());
    }

    /**
     * Its units but the first and last, the neighbours of the differences around it, folded as this
     * is, as the Synopsis has them; none where it has three units or fewer, all neighbours.
     */
    public @Nullable Fold between() {
      if (units.size() <= 3) {
        return null;
      }
      var inner = units.subList(1, units.size() - 1);
      return new Fold(same ? ChangesView.fold(inner) : span(inner), inner, note, same);
    }

    /**
     * The query that asks for its units, its first to its last, whatever their number:
     * first=anx%2Fpnt_8.1.2.3%2Fpnt_a&amp;last=anx%2Fpnt_8.1.2.3%2Fpnt_b
     */
    public String query() {
      return "first="
          + URLEncoder.encode(units.getFirst().eId(), StandardCharsets.UTF_8)
          + "&last="
          + URLEncoder.encode(units.getLast().eId(), StandardCharsets.UTF_8);
    }
  }

  /**
   * The opening of the unit the differences after it are in, as context.
   *
   * @param unit its num, heading and first block
   */
  public record Lead(Element unit) implements Passage {}

  /**
   * A difference, numbered.
   *
   * @param number its number, from 1, as its mark and the pager have it
   * @param difference the difference, its unit as shown
   * @param note what happened to it
   */
  public record Shown(int number, Difference difference, String note) implements Passage {
    /** Its kind, as its class has it: inserted, deleted, changed. */
    public String kind() {
      return difference.kind().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Its unit as shown, marked, in the list it is an item of in the text, as Read lays it out (its
     * number hanging beside its text); alone if it is in none.
     */
    public Element unit() {
      var list = difference.list();
      var shown = difference.shown();
      return list == null
          ? shown
          : new Element(list.name(), list.attributes(), List.of(shown), list.text());
    }

    /** Its unit as a ledger cites it: 1.2.3.8; a point after the point it is in, 1.2.3.5(e). */
    public String cited() {
      return ChangesView.cited(difference.unit());
    }
  }
}
