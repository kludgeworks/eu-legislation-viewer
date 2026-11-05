// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Acts by the words of their English title, as Cellar's full-text index finds and ranks them
 * (search.rq, beside this class): a page of them and one more, to say whether there are more.
 */
@Component
class CellarSearch implements Search {
  private static final Logger LOG = LoggerFactory.getLogger(CellarSearch.class);
  private static final String SEARCH = Sparql.template(CellarSearch.class, "search.rq");

  /** The order search.rq ranks them in: relevance, then amending none, in force, the newer. */
  private static final Comparator<Ranked> RANK =
      Comparator.comparingInt(Ranked::relevance)
          .reversed()
          .thenComparing(ranked -> !ranked.match().amends().isEmpty())
          .thenComparing(ranked -> !ranked.match().inForce())
          .thenComparing(ranked -> ranked.match().id().celex(), Comparator.reverseOrder());

  private final Sparql sparql;

  CellarSearch(Sparql sparql) {
    this.sparql = sparql;
  }

  @Override
  public Found matching(Query query) {
    var started = System.nanoTime();
    var found =
        found(
            sparql.select(
                Sparql.fill(
                    SEARCH,
                    Map.of(
                        "words", words(query.words()),
                        "types", types(query.kind()),
                        "amending", query.kind() == Kind.AMENDING ? "yes" : "no",
                        "limit", Integer.toString(query.size() + 1),
                        "offset", Integer.toString(query.offset())))),
            query.size());
    LOG.info(
        "Searched Cellar for {} ({}, from {}): {} acts{}, in {} ms",
        query.words(),
        query.kind().code(),
        query.offset(),
        found.acts().size(),
        found.more() ? " and more" : "",
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    return found;
  }

  /** The words as bif:contains takes them: 'aviation' AND 'security'. */
  static String words(List<String> words) {
    // letters and digits only (Query), so nothing in them is read as the expression's syntax
    return words.stream().map(word -> "'" + word + "'").collect(Collectors.joining(" AND "));
  }

  /** The CELEX type letters of {@code kind}'s acts: R, L, D, or all three. */
  private static String types(Kind kind) {
    return switch (kind) {
      case REGULATION -> "R";
      case DIRECTIVE -> "L";
      case DECISION -> "D";
      case ALL, AMENDING -> "RLD";
    };
  }

  /**
   * The answer to search.rq, folded into its acts and ordered, the first {@code size} of them.
   *
   * @throws CellarException if Cellar's answer makes no acts
   */
  static Found found(SparqlResult rows, int size) {
    try {
      var acts = new LinkedHashMap<String, Folding>();
      for (var row : rows.rows()) {
        var folding =
            acts.computeIfAbsent(
                row.get("celex"),
                celex ->
                    new Folding(
                        new ActIdentifier(celex),
                        row.get("title"),
                        (int) Double.parseDouble(row.get("relevance"))));
        row.find("eli").flatMap(Eli::parse).ifPresent(eli -> folding.eli = eli);
        row.find("type").map(ResourceType::ofUri).ifPresent(type -> folding.type = type);
        row.find("inForce").ifPresent(value -> folding.inForce |= isTrue(value));
        row.find("amends")
            .ifPresent(
                celex ->
                    folding.amends.putIfAbsent(
                        celex, row.find("amendsEli").flatMap(Eli::parse).orElse(null)));
      }
      var ranked = acts.values().stream().map(Folding::ranked).sorted(RANK).toList();
      return new Found(
          ranked.stream().limit(size).map(Ranked::match).toList(), ranked.size() > size);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new CellarException("Cellar's search answer makes no acts: " + e.getMessage(), e);
    }
  }

  /** An xsd:boolean as SPARQL JSON writes it ("true", or Virtuoso's "1"). */
  private static boolean isTrue(String value) {
    return "true".equals(value) || "1".equals(value);
  }

  /** A match and its rank. */
  private record Ranked(Match match, int relevance) {}

  /** A match, from its rows. */
  private static final class Folding {
    final ActIdentifier id;
    final String title;
    final int relevance;
    @Nullable Eli eli;
    @Nullable ResourceType type;
    boolean inForce;
    final Map<String, @Nullable Eli> amends = new LinkedHashMap<>();

    Folding(ActIdentifier id, String title, int relevance) {
      this.id = id;
      this.title = title;
      this.relevance = relevance;
    }

    Ranked ranked() {
      var amended = new ArrayList<Amends>();
      amends.forEach((celex, eli) -> amended.add(new Amends(new ActIdentifier(celex), eli)));
      return new Ranked(new Match(id, eli, type, title, inForce, amended), relevance);
    }
  }
}
