// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.ArrayList;
import java.util.HashMap;
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
          Map.entry("SCT", List.of("pnt", "sct", "level_SECTION", "level")),
          Map.entry("SECTION", List.of("pnt", "sct", "level_SECTION", "level")),
          Map.entry("SBS", List.of("pnt", "level")),
          Map.entry("PRT", List.of("prt", "level_PART", "level")),
          Map.entry("P", List.of("prt", "level_PART")),
          Map.entry("TIRE", List.of("idt")),
          Map.entry("AN", List.of("anx")),
          Map.entry("TIT", List.of("tis")),
          Map.entry("CHA", List.of("cpt", "chp", "pnt", "level_CHAPTER")),
          Map.entry("APP", List.of("level_ATTACHMENT", "app")));

  /**
   * Codes that may only frame what our eIds keep flat. A step with such a code is passed when no
   * unit has it.
   */
  private static final Set<String> FRAMES = Set.of("TIT", "CHA", "SCT", "SECTION", "SBS", "PRT");

  /**
   * Codes of a unit that our eIds do not always number. Where no unit has the number, the location
   * is in the unit found so far. {@code P} is a part of an annex in some acts and a paragraph of a
   * point in others.
   */
  private static final Set<String> WITHIN = Set.of("ALN", "PA", "P");

  /** Codes finer than our units. The location is in the unit that contains them. */
  private static final Set<String> FINER =
      Set.of("TXT", "TEXT", "FR", "L", "TAB", "TABL", "COL", "FOOTNOTE", "FIGURE");

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

  /**
   * Returns whether the numbered point {@code holder} of an annex holds the unit {@code eId} by its
   * number, where the text has the two beside each other. Some annexes keep a chapter flat: the
   * chapter is a point with only its heading, and its points and its attachments come after it. A
   * point numbered 3 holds:
   *
   * <ul>
   *   <li>the points beside it whose numbers start with "3.", for example {@code anx/pnt_3.0.1}
   *   <li>the attachments beside it whose numbers start with "3-", for example {@code
   *       anx/level_ATTACHMENT-3-B}
   *   <li>the units in these, for example {@code anx/pnt_3.1.3/pnt_a}
   * </ul>
   *
   * <p>It does not hold itself, and it does not hold the units below it, which are in it by their
   * eIds.
   */
  public static boolean holds(String holder, String eId) {
    var slash = holder.lastIndexOf('/');
    var step = holder.substring(slash + 1);
    var prefix = holder.substring(0, slash + 1);
    if (!step.startsWith("pnt_") || !eId.startsWith(prefix)) {
      return false;
    }
    var number = step.substring("pnt_".length());
    var rest = eId.substring(prefix.length());
    var end = rest.indexOf('/');
    var beside = end < 0 ? rest : rest.substring(0, end);
    return beside.startsWith("pnt_" + number + ".")
        || beside.startsWith("level_ATTACHMENT-" + number + "-");
  }

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
    return find(new Units(eIds), false);
  }

  /**
   * Returns the units of a text that it is, as {@link #in(List)} does. Use this to find many
   * locations in one text: {@code units} are made one time, {@link #units}.
   */
  public Optional<Found> in(Units units) {
    return find(units, false);
  }

  /** Returns the units of a text for {@link #in(Units)} and {@link #around(Units)}. */
  public static Units units(List<String> eIds) {
    return new Units(eIds);
  }

  /**
   * Returns the units of a text that it is, as {@link #in} does. If the text does not have it, it
   * returns the nearest unit that holds it, as within. For example, a point 4.1.3.4 that an act
   * inserts is in point 4.1.3 of a text from before the act. Returns empty if the text has no unit
   * that holds it, or if a code finds several units.
   */
  public Optional<Found> around(List<String> eIds) {
    return find(new Units(eIds), true);
  }

  /** Returns the units of a text that it is or that hold it, as {@link #around(List)} does. */
  public Optional<Found> around(Units units) {
    return find(units, true);
  }

  /**
   * Returns the units of a text that it is. If {@code around}, a code that finds no unit stops the
   * search at the unit found so far.
   */
  private Optional<Found> find(Units units, boolean around) {
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
        return around ? held(at) : Optional.empty();
      }
      if (!numbered || next == null) {
        if (!token.equals("AN")) {
          within = true;
          continue;
        }
        var annex = units.annex();
        if (annex == null) {
          return Optional.empty();
        }
        at = annex;
        continue;
      }
      i++;
      var number = number(next);
      if (number.contains("-") && (token.equals("PO") || token.equals("PTA"))) {
        return range(units, at, outer, names, number, within, around);
      }
      var found = below(units, at, outer, names, number);
      // a numbered paragraph coded as a point
      if (found.isEmpty() && (token.equals("PO") || token.equals("PT")) && isDigits(number)) {
        found = below(units, at, outer, List.of("par"), number);
      }
      // a numbered point coded as a paragraph, for example a definition of an article
      if (found.isEmpty() && token.equals("PA") && units.contains(at + "/pnt_" + number)) {
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
        // A subparagraph or a paragraph that our units do not number, for example one of a point.
        within = true;
      } else if (found.isEmpty() && around) {
        return holding(units, at, outer, names, number);
      } else {
        return Optional.empty();
      }
    }
    return at.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(at), within));
  }

  /**
   * Returns each unit of a range of points, for example {@code 11.3.2-11.3.5}, below {@code at}.
   * Returns empty if an end finds no unit or several. If {@code around}, and neither end is there,
   * returns {@code at} as the unit that holds the range.
   */
  private static Optional<Found> range(
      Units units,
      String at,
      @Nullable String outer,
      List<String> names,
      String number,
      boolean within,
      boolean around) {
    var ends = number.split("-", 2);
    var first = below(units, at, outer, names, ends[0]);
    var last = below(units, at, outer, names, ends[1]);
    if (first.size() == 1 && last.size() == 1) {
      return Optional.of(
          new Found(
              units.eIds.subList(
                  units.eIds.indexOf(first.getFirst()), units.eIds.indexOf(last.getFirst()) + 1),
              within));
    }
    return around && first.isEmpty() && last.isEmpty() ? held(at) : Optional.empty();
  }

  /**
   * Returns the unit that holds a unit numbered {@code number} that is not below {@code at}.
   *
   * <ul>
   *   <li>A point numbered 4.1.3.4 is in point 4.1.3, if there is one.
   *   <li>A unit whose number has a letter after it is at the unit with the number alone. An act
   *       inserts Article 18a after Article 18.
   *   <li>If not, it is in {@code at}.
   * </ul>
   */
  private static Optional<Found> holding(
      Units units, String at, @Nullable String outer, List<String> names, String number) {
    List<String> found = List.of();
    for (var upper = upper(number); upper != null && found.isEmpty(); upper = upper(upper)) {
      found = below(units, at, outer, names, upper);
    }
    var alone = withoutLetters(number);
    if (found.isEmpty() && !alone.equals(number)) {
      found = below(units, at, outer, names, alone);
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

  /** Returns {@code unit} as the unit that holds a location, or empty if it is no unit. */
  private static Optional<Found> held(String unit) {
    return unit.isEmpty() ? Optional.empty() : Optional.of(new Found(List.of(unit), true));
  }

  /**
   * Returns the number of the point that holds the point numbered {@code number}, or null if the
   * number has no parts. For example, 4.1.3 for 4.1.3.4.
   */
  private static @Nullable String upper(String number) {
    var dot = number.lastIndexOf('.');
    return dot > 0 ? number.substring(0, dot) : null;
  }

  /**
   * Returns the units named {@code names} and {@code number} below {@code at}. If there are none,
   * and {@code at} is a frame, returns those below {@code outer}, the unit that the frame is in.
   */
  private static List<String> below(
      Units units, String at, @Nullable String outer, List<String> names, String number) {
    var found = below(units, at, names, number);
    return found.isEmpty() && outer != null ? below(units, outer, names, number) : found;
  }

  /**
   * Returns the units named {@code names} and {@code number} below {@code at}. These are its
   * children if there are any, or else any of its descendants. The first name that a unit has
   * decides. A unit that has the number in the same case is tried first, because Cellar and our
   * eIds do not always agree on the case: Cellar writes {@code IXA} and {@code C)} where our eIds
   * have {@code IXa} and {@code c}.
   */
  private static List<String> below(Units units, String at, List<String> names, String number) {
    var prefix = at.isEmpty() ? "" : at + "/";
    var steps =
        names.stream()
            .map(name -> name + (name.startsWith("level_") ? "-" : "_") + number)
            .toList();
    for (var children : List.of(true, false)) {
      for (var exact : List.of(true, false)) {
        for (var step : steps) {
          var found =
              units.named(step).stream()
                  .filter(unit -> unit.startsWith(prefix))
                  .filter(unit -> !children || unit.indexOf('/', prefix.length()) < 0)
                  .filter(unit -> !exact || unit.substring(unit.lastIndexOf('/') + 1).equals(step))
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
   * The units of one text, by their eIds, made ready to find locations among them. To find many
   * locations in one text, make it one time.
   */
  public static final class Units {
    private final List<String> eIds;
    private final Set<String> all;

    /**
     * The units by the last step of their eIds in lower case, each list in the order of the eIds.
     */
    private final Map<String, List<String>> byStep = new HashMap<>();

    private final @Nullable String annex;

    private Units(List<String> eIds) {
      this.eIds = List.copyOf(eIds);
      this.all = new HashSet<>(eIds);
      var named = new HashMap<String, List<String>>();
      for (var unit : all.stream().sorted().toList()) {
        named
            .computeIfAbsent(
                unit.substring(unit.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT),
                step -> new ArrayList<>())
            .add(unit);
      }
      named.forEach((step, units) -> byStep.put(step, List.copyOf(units)));
      var annexes = eIds.stream().filter(e -> e.startsWith("anx") && !e.contains("/")).toList();
      this.annex = annexes.size() == 1 ? annexes.getFirst() : null;
    }

    /** Returns whether the text has the unit {@code eId}. */
    boolean contains(String eId) {
      return all.contains(eId);
    }

    /** Returns the eId of the one annex of the text, or null if it has none or several. */
    @Nullable String annex() {
      return annex;
    }

    /**
     * Returns the units whose eIds end with the step {@code step}, in any case, sorted by their
     * eIds.
     */
    List<String> named(String step) {
      return byStep.getOrDefault(step.toLowerCase(Locale.ROOT), List.of());
    }
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
          Map.entry("SCT", "Section"),
          Map.entry("SECTION", "Section"),
          Map.entry("SBS", "Subsection"),
          Map.entry("PRT", "Part"),
          Map.entry("TIRE", "indent"),
          Map.entry("AN", "Annex"),
          Map.entry("TIT", "Title"),
          Map.entry("CHA", "Chapter"),
          Map.entry("APP", "Appendix"));

  /**
   * Returns the unit that it names, as a page cites it, with the units that hold it. For example:
   *
   * <ul>
   *   <li>point 1.2.3
   *   <li>point 8.1.2.3(c), where a lettered point follows the point that it is in
   *   <li>Article 89(1)
   *   <li>Annex II, Part VI, Section 3.3
   *   <li>Appendix 4-C, point (c), indent 6
   *   <li>point 1.2.6.2, paragraph 2
   *   <li>the annex, for the one annex of an act.
   * </ul>
   *
   * <p>A unit that holds the next one is left out where the next one tells it: an article is
   * numbered across the act, point 6.8.1.6 is in chapter 6, and Appendix 5-A is in chapter 5. The
   * one annex of an act is left out too. What is finer than a unit, such as a sentence or a
   * subparagraph, is left out.
   */
  public String name() {
    var tokens = tokens();
    var parts = new ArrayList<String>();
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
      } else if (number.startsWith("(")
          && ("Article".equals(noun) || "point".equals(noun))
          && !cited.isEmpty()) {
        cited += number;
      } else if (token.equals("PA") && "Article".equals(noun)) {
        cited += "(" + number + ")";
      } else {
        if (token.equals("AR")) {
          parts.clear();
        } else if (noun != null && holds(noun, cited, number)) {
          parts.add(cited.isEmpty() ? noun : noun + " " + cited);
        }
        noun = NOUNS.get(token);
        cited = number;
      }
    }
    if (noun == null) {
      return code;
    }
    parts.add(cited.isEmpty() ? noun : noun + " " + cited);
    return String.join(", ", parts);
  }

  /**
   * Returns whether the name of a unit keeps the unit {@code noun} {@code cited} that holds it,
   * where the unit is numbered {@code number}. It does not keep the one annex of an act, or a unit
   * whose number starts the number of the unit: point 6.8.1.6 is in chapter 6.
   */
  private static boolean holds(String noun, String cited, String number) {
    return !cited.isEmpty() && !number.startsWith(cited + ".") && !number.startsWith(cited + "-")
        || !noun.equals("the annex") && cited.isEmpty();
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

  /** Returns a number without its brackets. For example, {@code (c)} and {@code C)} become c. */
  private static String number(String value) {
    return value.replaceAll("[()]", "");
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
