// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
   * How far a location goes in a text: the viewer reads its codes from the outermost in, and finds
   * the occurrence of each code below the occurrence of the code before it. The rules of the
   * feature "Places" tell what the place is ({@link Places}).
   */
  public sealed interface Walk {

    /**
     * Each code found its occurrence.
     *
     * @param eIds the occurrence, or for a range of points each occurrence from the first to the
     *     last, in document order
     * @param within whether the location is finer than the occurrence: it ends in a part that the
     *     text does not number, for example a sentence
     */
    record Reached(List<String> eIds, boolean within) implements Walk {
      public Reached {
        eIds = List.copyOf(eIds);
      }
    }

    /**
     * A code found no occurrence: the text does not have the place.
     *
     * @param at the occurrence found so far. Empty if the first code found none.
     * @param outer the occurrence that {@code at} is in, if {@code at} is a frame such as a
     *     chapter. What follows a frame can be beside it. Else null.
     * @param names the names of the code in an eId. Empty if the viewer does not know the code.
     * @param number the number that found no occurrence. Null for a code that the viewer does not
     *     know, and for a range of points.
     */
    record Missing(String at, @Nullable String outer, List<String> names, @Nullable String number)
        implements Walk {
      public Missing {
        names = List.copyOf(names);
      }
    }

    /** A code found several occurrences, or nothing that a place can be in. */
    record DeadEnd() implements Walk {}
  }

  /** Returns how far {@code location} goes in the text. */
  public Walk walk(Location location) {
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
        return new Walk.Missing(at, null, List.of(), null);
      }
      if (!numbered || next == null) {
        if (!token.equals("AN")) {
          within = true;
          continue;
        }
        if (annex == null) {
          return new Walk.DeadEnd();
        }
        at = annex;
        continue;
      }
      i++;
      var number = Location.number(next);
      if (number.contains("-") && (token.equals("PO") || token.equals("PTA"))) {
        return range(at, outer, names, number, within);
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
      } else if (found.isEmpty()) {
        return new Walk.Missing(at, outer, names, number);
      } else {
        return new Walk.DeadEnd();
      }
    }
    return at.isEmpty() ? new Walk.DeadEnd() : new Walk.Reached(List.of(at), within);
  }

  /**
   * Returns how far a range of points goes, for example {@code 11.3.2-11.3.5}, below {@code at}:
   * each occurrence from its first point to its last. The text does not have the range if neither
   * end is there.
   */
  private Walk range(
      String at, @Nullable String outer, List<String> names, String number, boolean within) {
    var ends = number.split("-", 2);
    var first = below(at, outer, names, ends[0]);
    var last = below(at, outer, names, ends[1]);
    if (first.size() == 1 && last.size() == 1) {
      return new Walk.Reached(
          eIds.subList(eIds.indexOf(first.getFirst()), eIds.indexOf(last.getFirst()) + 1), within);
    }
    return first.isEmpty() && last.isEmpty()
        ? new Walk.Missing(at, null, List.of(), null)
        : new Walk.DeadEnd();
  }

  /**
   * Returns the occurrences that have the names of {@code missing} and the number {@code number},
   * where the walk stopped.
   */
  public List<String> numbered(Walk.Missing missing, String number) {
    return below(missing.at(), missing.outer(), missing.names(), number);
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
                  .filter(occurrence ->
                      !exact
                          || occurrence.substring(occurrence.lastIndexOf('/') + 1).equals(segment))
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
