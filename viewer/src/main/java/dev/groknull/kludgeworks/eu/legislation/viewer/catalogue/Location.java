// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Place;
import java.util.ArrayList;
import java.util.List;
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
 * <p>{@link #in} finds the location among the occurrences of a text, {@link TextOccurrences}.
 *
 * <p>A code finer than our occurrences, such as a sentence or an indent of a table, leaves the
 * location at the occurrence that it is in.
 *
 * @param code the location as Cellar writes it, with its codes but without their URIs
 */
@ValueObject
public record Location(String code) {

  /** The eId names for each code, in the order tried. */
  static final Map<String, List<String>> NAMES =
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

  /** Codes finer than our occurrences. The location is in the occurrence that contains them. */
  static final Set<String> FINER =
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

  /** Returns the location in Cellar's annotation, without the URIs of its codes. */
  public static Location parse(String annotation) {
    return new Location(CODE.matcher(annotation).replaceAll("$1").strip());
  }

  /**
   * Returns the occurrences of a text that it is, among {@code eIds} in document order. Returns
   * empty where a code finds no occurrence or several occurrences. A range of points, for example
   * {@code PO 11.3.2-11.3.5}, is each occurrence from the first to the last.
   */
  public Optional<Found> in(List<String> eIds) {
    return in(TextOccurrences.of(eIds));
  }

  /**
   * Returns the occurrences of a text that it is, as {@link #in(List)} does. Use this to find many
   * locations in one text: {@code occurrences} are made one time.
   */
  @BusinessRule(group = RuleGroup.PLACES, name = "a-place-is-the-occurrences-it-names")
  @BusinessRule(group = RuleGroup.PLACES, name = "no-guess")
  public Optional<Found> in(TextOccurrences occurrences) {
    return occurrences.find(this, false);
  }

  /**
   * Returns the occurrences of a text that it is, as {@link #in} does. If the text does not have
   * it, it returns the nearest occurrence that holds it, as within. For example, a point 4.1.3.4
   * that an act inserts is in point 4.1.3 of a text from before the act. Returns empty if the text
   * has no occurrence that holds it, or if a code finds several occurrences.
   */
  public Optional<Found> around(List<String> eIds) {
    return around(TextOccurrences.of(eIds));
  }

  /**
   * Returns the occurrences of a text that it is or that hold it, as {@link #around(List)} does.
   */
  @BusinessRule(group = RuleGroup.PLACES, name = "a-place-the-text-does-not-have")
  public Optional<Found> around(TextOccurrences occurrences) {
    return occurrences.find(this, true);
  }

  /** What each code calls its occurrence, as a page names it. */
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

  /** The code of each noun of a place, but a point, whose code depends on its number. */
  private static final Map<String, String> CODES =
      Map.ofEntries(
          Map.entry("Article", "AR"),
          Map.entry("paragraph", "PA"),
          Map.entry("subparagraph", "ALN"),
          Map.entry("Section", "SCT"),
          Map.entry("Subsection", "SBS"),
          Map.entry("Part", "PRT"),
          Map.entry("indent", "TIRE"),
          Map.entry("Annex", "AN"),
          Map.entry("Title", "TIT"),
          Map.entry("Chapter", "CHA"),
          Map.entry("Appendix", "APP"));

  /**
   * Returns the place that it names, as a page cites it, with the occurrences that hold it ({@link
   * Place#of(List)}). For example, "point 8.1.2.3(c)", "Article 89(1)" or "Annex II, Part VI,
   * Section 3.3". A code that is not an occurrence, and what is finer than an occurrence, such as a
   * sentence, is left out. If no code names an occurrence, the place is the code.
   */
  public Place place() {
    var tokens = tokens();
    var subdivisions = new ArrayList<Place.Subdivision>();
    for (var i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);
      var next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      var numbered = isNumber(next);
      if (!NOUNS.containsKey(token)) {
        i += numbered ? 1 : 0;
        continue;
      }
      if (!numbered || next == null) {
        subdivisions.add(new Place.Subdivision(NOUNS.get(token), ""));
        continue;
      }
      i++;
      subdivisions.add(new Place.Subdivision(NOUNS.get(token), next.replace("\"", "")));
    }
    return Place.of(subdivisions).orElseGet(() -> new Place(code));
  }

  /**
   * Returns the location of {@code place}, as Cellar would code it. For example, "point 8.1.2.3(c)"
   * is {@code PO 8.1.2.3 PTA (c)}. {@link #in} then finds it among the occurrences of a text.
   *
   * @throws IllegalArgumentException if a subdivision of the place has no code
   */
  public static Location of(Place place) {
    var codes = new ArrayList<String>();
    for (var subdivision : place.subdivisions()) {
      var code =
          subdivision.noun().equals("point")
              ? (subdivision.number().startsWith("(") ? "PTA" : "PO")
              : CODES.get(subdivision.noun());
      if (code == null) {
        throw new IllegalArgumentException("No code for " + subdivision.noun() + " in " + place);
      }
      codes.add(subdivision.number().isEmpty() ? code : code + " " + subdivision.number());
    }
    return new Location(String.join(" ", codes));
  }

  /**
   * Returns its codes and numbers. Codes that Cellar runs together are split. For example, {@code
   * AR 11.5} becomes {@code AR 11 PA 5}.
   */
  List<String> tokens() {
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

  /** Returns whether {@code token}, the token after a code, is the number of the code. */
  static boolean isNumber(@Nullable String token) {
    return token != null && !NAMES.containsKey(token) && !FINER.contains(token);
  }

  /** Returns a number without its brackets. For example, {@code (c)} and {@code C)} become c. */
  static String number(String value) {
    return value.replaceAll("[()]", "");
  }

  /**
   * The occurrences that a location is.
   *
   * @param eIds the occurrence, or for a range each occurrence in it, in document order
   * @param within whether the location is finer than the occurrence that it is in. For example, a
   *     sentence, or a subparagraph that our occurrences do not number.
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
