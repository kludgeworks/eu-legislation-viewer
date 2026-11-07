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
 * Acts by the words of their title. The search runs on Cellar, in English, as one query when a
 * reader asks. The closest match comes first. It finds only regulations, directives and decisions.
 * These are the acts whose versions, or whose changes, a reader compares.
 */
@Repository
public interface Search {
  /** Returns a page of the acts that match {@code query}. */
  Found matching(Query query);

  /**
   * What kind of act a search is for, as the link of its filter names it. For example, {@code
   * ?type=regulation}.
   */
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

    /** Returns the kind with this code, or empty if there is no such kind. */
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
   * @param words the words of the title, as a reader typed them, in lower case. Each word has only
   *     letters and digits.
   * @param kind the kind of act
   * @param offset how many matches come before the page
   * @param size the most matches that the page has
   */
  @ValueObject
  record Query(List<String> words, Kind kind, int offset, int size) {
    /** Matches a word of letters and digits, in any script. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    /** The most words that a search takes. Words after them are left out. */
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
     * Returns the search for the words in {@code text}. What is between the words, such as
     * punctuation or a hyphen, is left out. Returns empty if the text has no words.
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
   * @param more whether there are more matches after them
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
   * @param amends the acts it amends, or an empty list for an act that amends none
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
