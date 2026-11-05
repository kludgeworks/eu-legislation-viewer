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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

  /** A num with a word of its own, and what follows it: Article 2, CHAPTER III, ANNEX I. */
  private static final Pattern NAMED = Pattern.compile("(\\p{L}{2,}) (.+)");

  public ChangesView {
    passages = List.copyOf(passages);
    numbers = List.copyOf(numbers);
  }

  /** {@code comparison}'s Changes view. */
  public static ChangesView of(Comparison comparison) {
    var differences = comparison.differences();
    var passages = new ArrayList<Passage>();
    var numbers = new ArrayList<Integer>();
    @Nullable Difference last = null;
    for (var difference : differences) {
      var lead = difference.lead();
      if (lead != null && !leads(last, difference)) {
        passages.add(new Lead(lead));
      }
      if (!difference.folded().isEmpty()) {
        passages.add(
            new Fold(
                fold(difference.folded()),
                difference.folded().stream().map(Sibling::eId).toList()));
      }
      var number = numbers.size() + 1;
      numbers.add(number);
      passages.add(new Shown(number, difference, note(difference)));
      last = difference;
    }
    return new ChangesView(count(differences.size()), breakdown(differences), passages, numbers);
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
   * Units the same in both texts, folded until asked for.
   *
   * @param label which: Points (a) and (b)
   * @param eIds their eIds, to load them by
   */
  public record Fold(String label, List<String> eIds) implements Passage {
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
  }
}
