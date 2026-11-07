// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.Repository;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Acts by the words of their title (Design: Home page): Cellar's, in English, one query when a
 * reader asks, the closest match first. Regulations, directives and decisions only, the acts a
 * reader compares the versions of, or the changes of.
 */
@Repository
public interface Search {
  /** A page of the acts matching {@code query}. */
  Found matching(Query query);

  /** What kind of act a search is for, as its filter's link names it (?type=regulation). */
  enum Kind {
    ALL("all", "All"),
    REGULATION("regulation", "Regulations"),
    DIRECTIVE("directive", "Directives"),
    DECISION("decision", "Decisions"),
    AMENDING("amending", "Amending acts");

    private final String code;
    private final String label;

    Kind(String code, String label) {
      this.code = code;
      this.label = label;
    }

    public String code() {
      return code;
    }

    public String label() {
      return label;
    }

    /** The kind by its code; none for one there isn't. */
    public static Optional<Kind> of(String code) {
      for (var kind : values()) {
        if (kind.code.equals(code)) {
          return Optional.of(kind);
        }
      }
      return Optional.empty();
    }
  }

  /**
   * A search.
   *
   * @param words the title's words, as a reader typed them: letters and digits, lower case
   * @param kind the kind of act
   * @param offset how many matches the page is after
   * @param size how many the page has, at most
   */
  @ValueObject
  record Query(List<String> words, Kind kind, int offset, int size) {
    /** A word: letters and digits, in any script. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    /** The most words a search takes: those after them are left out. */
    public static final int MOST_WORDS = 10;

    public Query {
      words = List.copyOf(words);
      if (words.isEmpty() || words.stream().anyMatch(w -> !WORD.matcher(w).matches())) {
        throw new IllegalArgumentException("Not a search's words: " + words);
      }
      if (offset < 0 || size < 1) {
        throw new IllegalArgumentException("Not a page: " + offset + ", " + size);
      }
    }

    /**
     * The search for the words in {@code text}, what is between them left out (punctuation, a
     * hyphen); none if it has none.
     */
    public static Optional<Query> of(String text, Kind kind, int offset, int size) {
      var words =
          WORD.matcher(text.toLowerCase(Locale.ROOT))
              .results()
              .limit(MOST_WORDS)
              .map(MatchResult::group)
              .toList();
      return words.isEmpty() ? Optional.empty() : Optional.of(new Query(words, kind, offset, size));
    }
  }

  /**
   * A page of matches.
   *
   * @param acts the acts, the closest match first
   * @param more whether there are more after them
   */
  @ValueObject
  record Found(List<Match> acts, boolean more) {
    public Found {
      acts = List.copyOf(acts);
    }
  }

  /**
   * An act matching a search.
   *
   * @param id its CELEX
   * @param eli its ELI, if Cellar has one
   * @param type its resource type, if Cellar has one
   * @param title its English title
   * @param inForce whether it is in force
   * @param amends the acts it amends, none for one amending none
   */
  @ValueObject
  record Match(
      ActIdentifier id,
      @Nullable Eli eli,
      @Nullable ResourceType type,
      String title,
      boolean inForce,
      List<Amends> amends) {
    public Match {
      amends = List.copyOf(amends);
    }
  }

  /**
   * An act a match amends.
   *
   * @param id its CELEX
   * @param eli its ELI, if Cellar has one
   */
  @ValueObject
  record Amends(ActIdentifier id, @Nullable Eli eli) {}
}
