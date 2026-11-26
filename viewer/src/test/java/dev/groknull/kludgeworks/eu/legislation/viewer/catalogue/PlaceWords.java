// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How the rules and the tests write a place: in words, as EUR-Lex names it ({@link
 * Places#aPageNamesAPlaceAsEurLexDoes}). The words of each code are Cellar's, from its table of
 * subdivisions as recorded in {@code /acts/subdivisions-fd_370.json}. For example, "annex point
 * 1.3.1.1 point (g)" is {@code AN PO 1.3.1.1 PTA (g)}.
 */
public final class PlaceWords {

  /** The codes of the subdivisions that a location finds. The others are not read back. */
  private static final Set<String> SUBDIVISIONS =
      Set.of(
          "AR", "PA", "ALN", "PO", "PTA", "PTI", "SCT", "SBS", "PRT", "TIRE", "AN", "TIT", "TIS",
          "CHA", "APP", "TXT", "FR", "TAB", "COL");

  /** A row of the recorded answer: the URI of a code's concept and its words. */
  private static final Pattern ROW =
      Pattern.compile(
          "\"value\"\\s*:\\s*\"[^\"]*/fd_370/([A-Z0-9_]+)\"[^}]*}\\s*,\\s*\"label\"[^}]*\"value\"\\s*:"
              + "\\s*\"([^\"]*)\"");

  /** The words of each code, by its code. */
  private static final Map<String, String> WORDS = words();

  private PlaceWords() {}

  /**
   * Returns the place that a location of only codes and numbers names, as {@link
   * Places#aPageNamesAPlaceAsEurLexDoes} names it. For example, "article 114 paragraph 1 unnumbered
   * paragraph 1" for {@code AR 114 PA 1 ALN 1}.
   */
  public static Place of(String code) {
    var words = new ArrayList<String>();
    for (var token : code.split(" ")) {
      words.add(WORDS.getOrDefault(token, token));
    }
    return new Place(String.join(" ", words));
  }

  /**
   * Returns the location that {@code place} names, in Cellar's codes. Each run of words that names
   * a code is that code. "point" is {@code PTA} before a number in brackets, and {@code PO} before
   * any other. Each other word is as it is: a number, or a word that Cellar does not code, such as
   * APP in an older annotation.
   */
  public static Location location(Place place) {
    var tokens = place.words().split(" ");
    var codes = new ArrayList<String>();
    var byWords =
        WORDS.entrySet().stream()
            .filter(each -> SUBDIVISIONS.contains(each.getKey()) && !each.getKey().equals("PTI"))
            .sorted(Comparator.comparingInt(each -> -each.getValue().split(" ").length))
            .toList();
    for (var i = 0; i < tokens.length; ) {
      String found = null;
      var length = 0;
      for (var each : byWords) {
        var named = each.getValue().split(" ");
        if (i + named.length <= tokens.length
            && String.join(" ", List.of(tokens).subList(i, i + named.length))
                .equals(each.getValue())) {
          found = each.getKey();
          length = named.length;
          break;
        }
      }
      if (found == null) {
        codes.add(tokens[i]);
        i++;
        continue;
      }
      if (found.equals("PO") || found.equals("PTA")) {
        var next = i + length < tokens.length ? tokens[i + length] : "";
        found = next.startsWith("(") ? "PTA" : "PO";
      }
      codes.add(found);
      i += length;
    }
    return new Location(String.join(" ", codes));
  }

  private static Map<String, String> words() {
    try (var in =
        Objects.requireNonNull(
            PlaceWords.class.getResourceAsStream("/acts/subdivisions-fd_370.json"),
            "/acts/subdivisions-fd_370.json")) {
      var answer = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      var words = new LinkedHashMap<String, String>();
      var row = ROW.matcher(answer);
      while (row.find()) {
        words.put(row.group(1), row.group(2));
      }
      return words;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
