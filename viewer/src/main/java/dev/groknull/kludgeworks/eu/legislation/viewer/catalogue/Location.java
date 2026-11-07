// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Where in an act an amendment acts, as Cellar codes it in {@code fd_370}. For example:
 *
 * <ul>
 *   <li>{@code AR 89 PA 1 ALN 1} is the first subparagraph of Article 89(1).
 *   <li>{@code AN PO 8.1.2.3 PTA (c)} is point 8.1.2.3(c) of the annex.
 *   <li>{@code AN I SCT I PTA (i)}.
 * </ul>
 *
 * <p>{@link #in} finds the location among the units of a text, by their eIds. Each code is one step
 * down, to the one unit below that has its number. A child is tried before any further descendant.
 * This is necessary because the frames of our eIds differ from those of Cellar. Our eIds keep some
 * frames flat, and add others, such as the levels of an annex. For example, a chapter of an annex
 * can be a point beside its own points, such as {@code anx/pnt_3} and {@code anx/pnt_3.0.1}. Or the
 * chapter is not there at all.
 *
 * <p>A code finer than our units, such as a sentence or an indent of a table, leaves the location
 * at the unit that it is in.
 *
 * @param code the location as Cellar writes it, with its codes but without their URIs
 */
@ValueObject
public record Location(String code) {

  /** The eId names for each code, in the order tried. */
  private static final Map<String, List<String>> STEPS =
      Map.ofEntries(
          Map.entry("AR", List.of("art")),
          Map.entry("PA", List.of("par")),
          Map.entry("ALN", List.of("sub", "unp")),
          Map.entry("PO", List.of("pnt")),
          Map.entry("PTA", List.of("pnt")),
          Map.entry("PT", List.of("pnt")),
          Map.entry("PTI", List.of("pnt")),
          Map.entry("SCT", List.of("pnt", "sct", "level")),
          Map.entry("SBS", List.of("pnt", "level")),
          Map.entry("PRT", List.of("prt", "level")),
          Map.entry("TIRE", List.of("idt")),
          Map.entry("AN", List.of("anx")),
          Map.entry("TIT", List.of("tis")),
          Map.entry("CHA", List.of("cpt", "chp", "pnt")),
          Map.entry("APP", List.of("level_ATTACHMENT")));

  /**
   * Codes that may only frame what our eIds keep flat. A step with such a code is passed when no
   * unit has it.
   */
  private static final Set<String> FRAMES = Set.of("TIT", "CHA", "SCT", "SBS", "PRT");

  /** Codes finer than our units. The location is in the unit that contains them. */
  private static final Set<String> FINER = Set.of("TXT", "TEXT", "FR", "L", "TAB", "COL");

  /** Matches an authority code as Cellar writes it, for example {@code {AR|http://…/fd_370/AR}}. */
  private static final Pattern CODE = Pattern.compile("\\{([A-Z0-9/-]+)\\|[^}]*}");

  /**
   * Matches an article and a paragraph run together, for example {@code AR 11.5} or {@code AR 1.4}.
   */
  private static final Pattern ARTICLE_PARAGRAPH = Pattern.compile("(\\d+[a-z]*)\\.(\\d+[a-z]*)");

  /**
   * Matches a paragraph without its code, for example the {@code .3} in {@code AR 81 .3}. The
   * suffix {@code BI} means bis, so {@code 5BI} is paragraph 5a.
   */
  private static final Pattern PARAGRAPH = Pattern.compile("\\.(\\d+)(BI)?");

  private static final Pattern ROMAN = Pattern.compile("[IVXLC]+");

  /** Returns the location in Cellar's annotation, without the URIs of its codes. */
  public static Location parse(String annotation) {
    return new Location(CODE.matcher(annotation).replaceAll("$1").strip());
  }

  /**
   * Returns the units of a text that it is, among {@code eIds} in document order. Returns empty
   * where a code finds no unit or several units. A range of points, for example {@code PO
   * 11.3.2-11.3.5}, is each unit from the first to the last.
   */
  public Optional<Found> in(List<String> eIds) {
    var units = new HashSet<>(eIds);
    var tokens = tokens();
    var at = "";
    // The unit in which a frame, such as a chapter, was found. What follows the frame may be
    // beside it, not in it.
    String outer = null;
    var within = false;
    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      var numbered = next != null && !STEPS.containsKey(next) && !FINER.contains(next);
      if (FINER.contains(token)) {
        within = true;
        i += numbered ? 1 : 0;
        continue;
      }
      var names = STEPS.get(token);
      if (names == null) {
        return Optional.empty();
      }
      if (!numbered || next == null) {
        if (!token.equals("AN")) {
          within = true;
          continue;
        }
        // the act's one annex
        var annexes = eIds.stream().filter(e -> e.startsWith("anx") && !e.contains("/")).toList();
        if (annexes.size() != 1) {
          return Optional.empty();
        }
        at = annexes.getFirst();
        continue;
      }
      i++;
      var number = token.equals("APP") ? next.replaceAll("[()]", "") : number(next);
      if (number.contains("-") && (token.equals("PO") || token.equals("PTA"))) {
        var ends = number.split("-", 2);
        var first = below(units, at, outer, names, ends[0]);
        var last = below(units, at, outer, names, ends[1]);
        if (first.size() != 1 || last.size() != 1) {
          return Optional.empty();
        }
        return Optional.of(
            new Found(
                eIds.subList(eIds.indexOf(first.getFirst()), eIds.indexOf(last.getFirst()) + 1),
                within));
      }
      var found = below(units, at, outer, names, number);
      // a numbered paragraph coded as a point
      if (found.isEmpty() && (token.equals("PO") || token.equals("PT")) && isDigits(number)) {
        found = below(units, at, outer, List.of("par"), number);
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
      } else if (found.isEmpty() && (token.equals("ALN") || token.equals("PA"))) {
        // A subparagraph or a paragraph that our units do not number, for example one of a point.
        within = true;
      } else {
        return Optional.empty();
      }
    }
    return at.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(at), within));
  }

  /**
   * Returns the units named {@code names} and {@code number} below {@code at}. If there are none,
   * and {@code at} is a frame, returns those below {@code outer}, the unit that the frame is in.
   */
  private static List<String> below(
      Set<String> units, String at, @Nullable String outer, List<String> names, String number) {
    var found = below(units, at, names, number);
    return found.isEmpty() && outer != null ? below(units, outer, names, number) : found;
  }

  /**
   * Returns the units named {@code names} and {@code number} below {@code at}. These are its
   * children if there are any, or else any of its descendants.
   */
  private static List<String> below(
      Set<String> units, String at, List<String> names, String number) {
    var prefix = at.isEmpty() ? "" : at + "/";
    var steps = new ArrayList<String>();
    for (var name : names) {
      steps.add(name + (name.equals("level_ATTACHMENT") ? "-" : "_") + number);
    }
    var children = steps.stream().map(step -> prefix + step).filter(units::contains).toList();
    if (!children.isEmpty()) {
      return children;
    }
    return units.stream()
        .filter(unit -> unit.startsWith(prefix))
        .filter(unit -> steps.contains(unit.substring(unit.lastIndexOf('/') + 1)))
        .sorted()
        .toList();
  }

  /** What each code calls its unit, as a page names it. */
  private static final Map<String, String> NOUNS =
      Map.ofEntries(
          Map.entry("AR", "Article"),
          Map.entry("PA", "paragraph"),
          Map.entry("ALN", "subparagraph"),
          Map.entry("PO", "point"),
          Map.entry("PTA", "point"),
          Map.entry("PT", "point"),
          Map.entry("PTI", "point"),
          Map.entry("SCT", "section"),
          Map.entry("SBS", "subsection"),
          Map.entry("PRT", "part"),
          Map.entry("TIRE", "indent"),
          Map.entry("AN", "Annex"),
          Map.entry("TIT", "Title"),
          Map.entry("CHA", "chapter"),
          Map.entry("APP", "Appendix"));

  /**
   * Returns the unit that it names, as a page cites it. For example:
   *
   * <ul>
   *   <li>point 1.2.3
   *   <li>point 8.1.2.3(c), where a lettered point follows the point that it is in
   *   <li>Article 89(1)
   *   <li>the annex, for the one annex of an act.
   * </ul>
   *
   * <p>What is finer than a unit, such as a sentence or a subparagraph, is left out.
   */
  public String name() {
    var tokens = tokens();
    String noun = null;
    var cited = "";
    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      var numbered = next != null && !STEPS.containsKey(next) && !FINER.contains(next);
      if (!NOUNS.containsKey(token)) {
        i += numbered ? 1 : 0;
        continue;
      }
      if (!numbered || next == null) {
        if (token.equals("AN")) {
          noun = "the annex";
          cited = "";
        }
        continue;
      }
      i++;
      var number = next.replace("\"", "");
      // A point (c) of point 8.1.2.3, or a paragraph (1) of Article 89, is cited after the unit
      // that it is in. A subparagraph is not cited.
      if (token.equals("ALN") && noun != null) {
        continue;
      } else if (number.startsWith("(") && noun != null && !cited.isEmpty()) {
        cited += number;
      } else if (token.equals("PA") && "Article".equals(noun)) {
        cited += "(" + number + ")";
      } else {
        noun = NOUNS.get(token);
        cited = number;
      }
    }
    if (noun == null) {
      return code;
    }
    return cited.isEmpty() ? noun : noun + " " + cited;
  }

  /**
   * Returns its codes and numbers. Codes that Cellar runs together are split. For example, {@code
   * AR 11.5} becomes {@code AR 11 PA 5}.
   */
  private List<String> tokens() {
    var tokens = new ArrayList<String>();
    for (var word : code.replace("\"", "").split("\\s+")) {
      var articleParagraph = ARTICLE_PARAGRAPH.matcher(word);
      var paragraph = PARAGRAPH.matcher(word);
      if (articleParagraph.matches() && !tokens.isEmpty() && tokens.getLast().equals("AR")) {
        tokens.addAll(List.of(articleParagraph.group(1), "PA", articleParagraph.group(2)));
      } else if (paragraph.matches()) {
        tokens.addAll(List.of("PA", paragraph.group(1) + (paragraph.group(2) == null ? "" : "a")));
      } else if (!word.isEmpty()) {
        tokens.add(word);
      }
    }
    return tokens;
  }

  /**
   * Returns a number as our eIds write it. For example, {@code (c)} and {@code C)} become {@code
   * c}. Roman numerals, such as I or IV, stay as they are.
   */
  private static String number(String value) {
    var bare = value.replaceAll("[()]", "");
    return ROMAN.matcher(bare).matches() ? bare : bare.toLowerCase(Locale.ROOT);
  }

  private static boolean isDigits(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }

  /**
   * The units that a location is.
   *
   * @param eIds the unit, or for a range each unit in it, in document order
   * @param within whether the location is finer than the unit that it is in. For example, a
   *     sentence, or a subparagraph that our units do not number.
   */
  @ValueObject
  public record Found(List<String> eIds, boolean within) {
    public Found {
      eIds = List.copyOf(eIds);
    }
  }

  /** {@inheritDoc} It is the location as Cellar writes it, without the URIs of its codes. */
  @Override
  public String toString() {
    return code;
  }
}
