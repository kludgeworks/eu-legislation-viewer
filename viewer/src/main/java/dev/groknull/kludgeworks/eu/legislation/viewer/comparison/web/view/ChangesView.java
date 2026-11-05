// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A comparison's Changes view (Design: Mode: Compare, Changes): only the provisions that differ, in
 * the later text's order, each numbered, with a note of what happened to it; before one, the
 * opening of the unit it is in, as context, where the one before wasn't in it, then the units
 * before it there that are the same in both, folded (Points (a) and (b) — no differences).
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
    var pending = new Pending();
    @Nullable Difference last = null;
    for (var difference : comparison.differences()) {
      if (!shown.test(difference)) {
        pending.add(difference.folded(), difference.parent());
        if (difference.kind() != Kind.DELETED
            && differences.stream().noneMatch(d -> d.eId().startsWith(difference.eId() + "/"))) {
          pending.add(List.of(difference.unit()), difference.parent());
          pending.hidden++;
        }
        continue;
      }
      var lead = difference.lead();
      var opening = lead == null || leads(last, difference) ? null : new Lead(lead);
      if (opening != null) {
        // units folded before it in other units than its own go before its opening
        if (!pending.in(difference.parent())) {
          pending.fold(others).ifPresent(passages::add);
        }
        passages.add(opening);
      }
      pending.add(difference.folded(), difference.parent());
      pending.fold(others).ifPresent(passages::add);
      var number = numbers.size() + 1;
      numbers.add(number);
      passages.add(new Shown(number, difference, note(difference)));
      last = difference;
    }
    pending.fold(others).ifPresent(passages::add);
    return new ChangesView(count(differences.size()), breakdown(differences), passages, numbers);
  }

  /** Units waiting to be folded, in order, each once, and how many of them differ. */
  private static final class Pending {
    private final List<Sibling> units = new ArrayList<>();
    private final Set<@Nullable String> parents = new HashSet<>();
    private int hidden;

    /** {@code siblings}, in {@code parent}, but for those already in, or in one that is. */
    void add(List<Sibling> siblings, @Nullable String parent) {
      for (var sibling : siblings) {
        if (units.stream()
            .noneMatch(
                unit ->
                    unit.eId().equals(sibling.eId())
                        || sibling.eId().startsWith(unit.eId() + "/"))) {
          units.add(sibling);
          parents.add(parent);
        }
      }
    }

    /** Whether they are all in {@code parent}, if there are any. */
    boolean in(@Nullable String parent) {
      return parents.isEmpty() || (parents.size() == 1 && parents.contains(parent));
    }

    /**
     * Their fold, emptying them: no differences, or as {@code others} says, where some of them
     * differ (Points 11.1.3 to 11.2.7(b) (21 provisions)); none if there are none.
     */
    Optional<Fold> fold(String others) {
      if (units.isEmpty()) {
        return Optional.empty();
      }
      var eIds = units.stream().map(Sibling::eId).toList();
      var fold =
          hidden == 0
              ? new Fold(ChangesView.fold(units), eIds, "no differences")
              : new Fold(span(units), eIds, others);
      units.clear();
      parents.clear();
      hidden = 0;
      return Optional.of(fold);
    }
  }

  /** Whether {@code last} already leads to {@code next}: next is in it, or in the same unit. */
  private static boolean leads(@Nullable Difference last, Difference next) {
    return last != null
        && (Objects.equals(last.eId(), next.parent())
            || Objects.equals(last.parent(), next.parent()));
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
   * mark for one (an indent's dash), counted (2 indents; 3 subparagraphs).
   */
  static String fold(List<Sibling> siblings) {
    var first = siblings.getFirst();
    var last = siblings.getLast();
    var join = siblings.size() == 2 ? " and " : " to ";
    if (siblings.stream().anyMatch(sibling -> !WORD.matcher(sibling.num()).find())) {
      var noun = noun(first.kind()).toLowerCase(Locale.ROOT);
      return siblings.size() + " " + (siblings.size() == 1 ? noun : plural(noun));
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
    var noun = noun(first.kind());
    var named = NAMED.matcher(first.num()).matches();
    if (first == last) {
      return (named ? cited(first) : noun + " " + cited(first)) + " (" + count + ")";
    }
    return (named ? cited(first) : plural(noun) + " " + cited(first))
        + " to "
        + cited(last)
        + " ("
        + count
        + ")";
  }

  /** A unit's num, a point's after the numbered point it is in: 11.2.7(b). */
  private static String cited(Sibling unit) {
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
   * Units folded until asked for: the same in both texts, or, under an act filter, changed by
   * others.
   *
   * @param label which: Points (a) and (b)
   * @param eIds their eIds, to load them by
   * @param note why they are folded: no differences; not changed by 2019/1583
   */
  public record Fold(String label, List<String> eIds, String note) implements Passage {
    public Fold {
      eIds = List.copyOf(eIds);
    }

    /** The query that asks for its units by eId: eId=anx%2Fpnt_8.1.2.3%2Fpnt_a&amp;eId=… */
    public String query() {
      return eIds.stream()
          .map(eId -> "eId=" + URLEncoder.encode(eId, StandardCharsets.UTF_8))
          .collect(Collectors.joining("&"));
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

    /** Its unit as a ledger cites it: 1.2.3.8; a point after the point it is in, 1.2.3.5(e). */
    public String cited() {
      return ChangesView.cited(difference.unit());
    }
  }
}
