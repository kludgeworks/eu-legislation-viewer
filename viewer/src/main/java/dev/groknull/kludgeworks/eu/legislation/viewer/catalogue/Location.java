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
 * Where in an act an amendment acts, as Cellar codes it (fd_370): AR 89 PA 1 ALN 1 (Article 89(1),
 * first subparagraph), AN PO 8.1.2.3 PTA (c) (the annex's point 8.1.2.3(c)), AN I SCT I PTA (i).
 * Found among a text's units by their eIds (Research: joining Cellar's locations to our units):
 * each code is one step down, to the one unit below with its number, a child before any further
 * descendant, as the frames our eIds keep flat or add (an annex's levels) differ from Cellar's: a
 * chapter of an annex can be a point beside its own (anx/pnt_3, anx/pnt_3.0.1), or not there at
 * all. Codes finer than our units (a sentence, an indent of a table) leave the unit they are in.
 *
 * @param code the location as Cellar writes it, its codes without their URIs
 */
@ValueObject
public record Location(String code) {

  /** Each code's eId names, in the order tried. */
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

  /** Codes that may only frame what our eIds keep flat: passed when no unit has them. */
  private static final Set<String> FRAMES = Set.of("TIT", "CHA", "SCT", "SBS", "PRT");

  /** Codes finer than our units: the location is in the unit they are in. */
  private static final Set<String> FINER = Set.of("TXT", "TEXT", "FR", "L", "TAB", "COL");

  /** An authority code as Cellar writes it: {AR|http://…/fd_370/AR}. */
  private static final Pattern CODE = Pattern.compile("\\{([A-Z0-9/-]+)\\|[^}]*}");

  /** An article and paragraph run together: AR 11.5, AR 1.4. */
  private static final Pattern ARTICLE_PARAGRAPH = Pattern.compile("(\\d+[a-z]*)\\.(\\d+[a-z]*)");

  /** A paragraph without its code: AR 81 .3; 5BI is 5a (bis). */
  private static final Pattern PARAGRAPH = Pattern.compile("\\.(\\d+)(BI)?");

  private static final Pattern ROMAN = Pattern.compile("[IVXLC]+");

  /** The location in Cellar's annotation, its codes' URIs dropped. */
  public static Location parse(String annotation) {
    return new Location(CODE.matcher(annotation).replaceAll("$1").strip());
  }

  /**
   * The units of a text it is, among {@code eIds} (in document order); none where a code finds no
   * unit or several. A range of points (PO 11.3.2-11.3.5) is each unit from one to the other.
   */
  public Optional<Found> in(List<String> eIds) {
    var units = new HashSet<>(eIds);
    var tokens = tokens();
    var at = "";
    // the unit a frame (a chapter) was found in: what follows it may be beside it, not in it
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
        // a subparagraph or paragraph our units don't number (a point's)
        within = true;
      } else {
        return Optional.empty();
      }
    }
    return at.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(at), within));
  }

  /**
   * The units named {@code names} and {@code number} below {@code at}, or else, where it is a
   * frame, below {@code outer}, the unit it is in.
   */
  private static List<String> below(
      Set<String> units, String at, @Nullable String outer, List<String> names, String number) {
    var found = below(units, at, names, number);
    return found.isEmpty() && outer != null ? below(units, outer, names, number) : found;
  }

  /**
   * The units named {@code names} and {@code number} below {@code at}: its child, or else any of
   * its descendants.
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

  /** Its codes and numbers, with the run-together ones Cellar has apart: AR 11.5 is AR 11 PA 5. */
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

  /** A number as our eIds write it: (c) and C) are c; roman numerals (I, IV) as they are. */
  private static String number(String value) {
    var bare = value.replaceAll("[()]", "");
    return ROMAN.matcher(bare).matches() ? bare : bare.toLowerCase(Locale.ROOT);
  }

  private static boolean isDigits(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }

  /**
   * The units a location is.
   *
   * @param eIds the unit, or for a range each unit in it, in document order
   * @param within whether the location is finer than the unit, which it is in: a sentence, a
   *     subparagraph our units don't number
   */
  @ValueObject
  public record Found(List<String> eIds, boolean within) {
    public Found {
      eIds = List.copyOf(eIds);
    }
  }

  /** {@inheritDoc} As Cellar writes it, without its codes' URIs. */
  @Override
  public String toString() {
    return code;
  }
}
