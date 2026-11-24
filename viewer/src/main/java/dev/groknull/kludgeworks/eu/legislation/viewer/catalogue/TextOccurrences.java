// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The occurrences of one text, by their eIds, made ready to find {@link Location locations} among
 * them. To find many locations in one text, make it one time.
 *
 * <p>Each code of a location is one level down, to the one occurrence below that has its number. A
 * child is tried before any further descendant. This is necessary because the frames of our eIds
 * differ from those of Cellar. Our eIds keep some frames flat, and add others, such as the levels
 * of an annex. For example, a chapter of an annex can be a point beside its own points, such as
 * {@code anx/pnt_3} and {@code anx/pnt_3.0.1}. Or the chapter is not there at all.
 */
public final class TextOccurrences {

  /**
   * Codes that may only frame what our eIds keep flat. Such a code is passed when no occurrence has
   * it.
   */
  private static final Set<String> FRAMES = Set.of("TIT", "CHA", "SCT", "SECTION", "SBS", "PRT");

  /**
   * Codes of an occurrence that our eIds do not always number. Where no occurrence has the number,
   * the location is in the occurrence found so far. {@code P} is a part of an annex in some acts
   * and a paragraph of a point in others.
   */
  private static final Set<String> WITHIN = Set.of("ALN", "PA", "P");

  private final List<String> eIds;
  private final Set<String> all;

  /**
   * The occurrences by the last segment of their eIds in lower case, each list in the order of the
   * eIds.
   */
  private final Map<String, List<String>> bySegment = new HashMap<>();

  private final @Nullable String annex;

  private TextOccurrences(List<String> eIds) {
    this.eIds = List.copyOf(eIds);
    this.all = new HashSet<>(eIds);
    var named = new HashMap<String, List<String>>();
    for (var occurrence : all.stream().sorted().toList()) {
      named
          .computeIfAbsent(
              occurrence.substring(occurrence.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT),
              segment -> new ArrayList<>())
          .add(occurrence);
    }
    named.forEach((segment, occurrences) -> bySegment.put(segment, List.copyOf(occurrences)));
    var annexes = eIds.stream().filter(e -> e.startsWith("anx") && !e.contains("/")).toList();
    this.annex = annexes.size() == 1 ? annexes.getFirst() : null;
  }

  /** Returns the occurrences of a text, {@code eIds} in document order. */
  public static TextOccurrences of(List<String> eIds) {
    return new TextOccurrences(eIds);
  }

  /**
   * Returns whether the numbered point {@code holder} of an annex holds the occurrence {@code eId}
   * by its number, where the text has the two beside each other. Some annexes keep a chapter flat:
   * the chapter is a point with only its heading, and its points and its attachments come after it.
   * A point numbered 3 holds:
   *
   * <ul>
   *   <li>the points beside it whose numbers start with "3.", for example {@code anx/pnt_3.0.1}
   *   <li>the attachments beside it whose numbers start with "3-", for example {@code
   *       anx/level_ATTACHMENT-3-B}
   *   <li>the occurrences in these, for example {@code anx/pnt_3.1.3/pnt_a}
   * </ul>
   *
   * <p>It does not hold itself, and it does not hold the occurrences below it, which are in it by
   * their eIds.
   */
  public static boolean holds(String holder, String eId) {
    var slash = holder.lastIndexOf('/');
    var segment = holder.substring(slash + 1);
    var prefix = holder.substring(0, slash + 1);
    if (!segment.startsWith("pnt_") || !eId.startsWith(prefix)) {
      return false;
    }
    var number = segment.substring("pnt_".length());
    var rest = eId.substring(prefix.length());
    var end = rest.indexOf('/');
    var beside = end < 0 ? rest : rest.substring(0, end);
    return beside.startsWith("pnt_" + number + ".")
        || beside.startsWith("level_ATTACHMENT-" + number + "-");
  }

  /**
   * Returns the occurrences that {@code location} is. If {@code around}, a code that finds no
   * occurrence stops the search at the occurrence found so far.
   */
  Optional<Found> find(Location location, boolean around) {
    var tokens = location.tokens();
    var at = "";
    // The occurrence in which a frame, such as a chapter, was found. What follows the frame may be
    // beside it, not in it.
    String outer = null;
    var within = false;
    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      var numbered = Location.isNumber(next);
      if (Location.FINER.contains(token)) {
        within = true;
        i += numbered ? 1 : 0;
        continue;
      }
      var names = Location.NAMES.get(token);
      if (names == null) {
        return around ? held(at) : Optional.empty();
      }
      if (!numbered || next == null) {
        if (!token.equals("AN")) {
          within = true;
          continue;
        }
        if (annex == null) {
          return Optional.empty();
        }
        at = annex;
        continue;
      }
      i++;
      var number = Location.number(next);
      if (number.contains("-") && (token.equals("PO") || token.equals("PTA"))) {
        return range(at, outer, names, number, within, around);
      }
      var found = below(at, outer, names, number);
      // a numbered paragraph coded as a point
      if (found.isEmpty() && (token.equals("PO") || token.equals("PT")) && isDigits(number)) {
        found = below(at, outer, List.of("par"), number);
      }
      // a numbered point coded as a paragraph, for example a definition of an article
      if (found.isEmpty() && token.equals("PA") && all.contains(at + "/pnt_" + number)) {
        found = List.of(at + "/pnt_" + number);
      }
      var in = at;
      outer = null;
      if (found.size() == 1) {
        at = found.getFirst();
        if (FRAMES.contains(token)) {
          outer = in;
        }
      } else if (found.isEmpty() && FRAMES.contains(token)) {
        // a frame our eIds don't have
      } else if (found.isEmpty() && WITHIN.contains(token)) {
        // A subparagraph or a paragraph that our occurrences do not number, for example one of a
        // point.
        within = true;
      } else if (found.isEmpty() && around) {
        return holding(at, outer, names, number);
      } else {
        return Optional.empty();
      }
    }
    return at.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(at), within));
  }

  /**
   * Returns each occurrence of a range of points, for example {@code 11.3.2-11.3.5}, below {@code
   * at}. Returns empty if an end finds no occurrence or several. If {@code around}, and neither end
   * is there, returns {@code at} as the occurrence that holds the range.
   */
  private Optional<Found> range(
      String at,
      @Nullable String outer,
      List<String> names,
      String number,
      boolean within,
      boolean around) {
    var ends = number.split("-", 2);
    var first = below(at, outer, names, ends[0]);
    var last = below(at, outer, names, ends[1]);
    if (first.size() == 1 && last.size() == 1) {
      return Optional.of(
          new Found(
              eIds.subList(eIds.indexOf(first.getFirst()), eIds.indexOf(last.getFirst()) + 1),
              within));
    }
    return around && first.isEmpty() && last.isEmpty() ? held(at) : Optional.empty();
  }

  /**
   * Returns the occurrence that holds an occurrence numbered {@code number} that is not below
   * {@code at}.
   *
   * <ul>
   *   <li>A point numbered 4.1.3.4 is in point 4.1.3, if there is one.
   *   <li>An occurrence whose number has a letter after it is at the occurrence with the number
   *       alone. An act inserts Article 18a after Article 18.
   *   <li>If not, it is in {@code at}.
   * </ul>
   */
  private Optional<Found> holding(
      String at, @Nullable String outer, List<String> names, String number) {
    List<String> found = List.of();
    for (var upper = upper(number); upper != null && found.isEmpty(); upper = upper(upper)) {
      found = below(at, outer, names, upper);
    }
    var alone = withoutLetters(number);
    if (found.isEmpty() && !alone.equals(number)) {
      found = below(at, outer, names, alone);
    }
    return held(found.size() == 1 ? found.getFirst() : at);
  }

  /**
   * Returns {@code number} without the letters after it. For example, 18 for 18a. Returns {@code
   * number} as it is if it does not start with a digit, or if it has no letter after it.
   */
  private static String withoutLetters(String number) {
    var end = number.length();
    while (end > 0 && Character.isLetter(number.charAt(end - 1))) {
      end--;
    }
    return end > 0 && isDigits(number.substring(0, end)) ? number.substring(0, end) : number;
  }

  /**
   * Returns {@code occurrence} as the occurrence that holds a location, or empty if it is no
   * occurrence.
   */
  private static Optional<Found> held(String occurrence) {
    return occurrence.isEmpty()
        ? Optional.empty()
        : Optional.of(new Found(List.of(occurrence), true));
  }

  /**
   * Returns the number of the point that holds the point numbered {@code number}, or null if the
   * number has no dot. For example, 4.1.3 for 4.1.3.4.
   */
  private static @Nullable String upper(String number) {
    var dot = number.lastIndexOf('.');
    return dot > 0 ? number.substring(0, dot) : null;
  }

  /**
   * Returns the occurrences named {@code names} and {@code number} below {@code at}. If there are
   * none, and {@code at} is a frame, returns those below {@code outer}, the occurrence that the
   * frame is in.
   */
  private List<String> below(String at, @Nullable String outer, List<String> names, String number) {
    var found = below(at, names, number);
    return found.isEmpty() && outer != null ? below(outer, names, number) : found;
  }

  /**
   * Returns the occurrences named {@code names} and {@code number} below {@code at}. These are its
   * children if there are any, or else any of its descendants. The first name that an occurrence
   * has decides. An occurrence that has the number in the same case is tried first, because Cellar
   * and our eIds do not always agree on the case: Cellar writes {@code IXA} and {@code C)} where
   * our eIds have {@code IXa} and {@code c}.
   */
  private List<String> below(String at, List<String> names, String number) {
    var prefix = at.isEmpty() ? "" : at + "/";
    var segments =
        names.stream()
            .map(name -> name + (name.startsWith("level_") ? "-" : "_") + number)
            .toList();
    for (var children : List.of(true, false)) {
      for (var exact : List.of(true, false)) {
        for (var segment : segments) {
          var found =
              named(segment).stream()
                  .filter(occurrence -> occurrence.startsWith(prefix))
                  .filter(occurrence -> !children || occurrence.indexOf('/', prefix.length()) < 0)
                  .filter(
                      occurrence ->
                          !exact
                              || occurrence
                                  .substring(occurrence.lastIndexOf('/') + 1)
                                  .equals(segment))
                  .toList();
          if (!found.isEmpty()) {
            return found;
          }
        }
      }
    }
    return List.of();
  }

  /**
   * Returns the occurrences whose eIds end with the segment {@code segment}, in any case, sorted by
   * their eIds.
   */
  private List<String> named(String segment) {
    return bySegment.getOrDefault(segment.toLowerCase(Locale.ROOT), List.of());
  }

  private static boolean isDigits(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }
}
