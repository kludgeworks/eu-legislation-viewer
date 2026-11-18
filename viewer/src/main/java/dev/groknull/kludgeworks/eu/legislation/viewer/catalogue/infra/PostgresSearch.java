// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_ALIAS;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_WORK;
import static org.jooq.impl.DSL.substring;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
class PostgresSearch implements Search {
  private static final Logger LOG = LoggerFactory.getLogger(PostgresSearch.class);
  private static final String SEARCH = SearchSql.read("index-search.sql");
  private static final Pattern SEARCH_WORD = Pattern.compile("[\\p{L}\\p{N}]+");
  private static final Pattern CITATION =
      Pattern.compile(
          "(?:(?:(implementing|delegated)\\s+)?(regulation|directive|decision)\\s+(?:\\((?:eu|ec|eec)\\)\\s+)?(?:no\\s+)?)?"
              + "(\\d{1,4})\\s*/\\s*(\\d{1,4})(?:/(?:eu|ec|eec))?",
          Pattern.CASE_INSENSITIVE);
  private final DSLContext catalogue;
  private final JdbcTemplate db;
  private final CellarSearch cellar;
  private final TransactionOperations transactions;

  private final ObservationRegistry observations;

  PostgresSearch(
      DSLContext catalogue,
      JdbcTemplate db,
      Sparql sparql,
      TransactionOperations transactions,
      ObservationRegistry observations) {
    this.observations = observations;
    this.catalogue = catalogue;
    this.db = db;
    this.transactions = transactions;
    this.cellar = new CellarSearch(sparql);
  }

  @Override
  public Optional<ActIdentifier> identified(String typed) {
    var citation = CITATION.matcher(typed.strip());
    boolean numbered = citation.matches();
    return Observation.createNotStarted("catalogue.search.identify", observations)
        .lowCardinalityKeyValue("method", numbered ? "citation" : "alias")
        .observe(() -> identify(typed, citation, numbered));
  }

  private Optional<ActIdentifier> identify(String typed, Matcher citation, boolean numbered) {
    List<ActIdentifier> ids;
    if (numbered) {
      int left = Integer.parseInt(citation.group(3));
      int right = Integer.parseInt(citation.group(4));
      String kind =
          switch (Objects.toString(citation.group(2), "").toLowerCase(Locale.ROOT)) {
            case "regulation" -> "R";
            case "directive" -> "L";
            case "decision" -> "D";
            default -> "";
          };
      String subtype =
          switch (Objects.toString(citation.group(1), "").toLowerCase(Locale.ROOT)) {
            case "implementing" -> "IMPL";
            case "delegated" -> "DEL";
            default -> "";
          };
      // Pre-2000 directives may cite the year with two digits, as in 95/46/EC.
      if (!kind.isEmpty()
          && citation.group(3).length() == 2
          && citation.group(4).length() < 4
          && left >= 50) {
        left += 1900;
      }
      var work = CATALOGUE_SEARCH_WORK;
      var condition =
          work.YEAR
              .eq(left)
              .and(work.NATURAL_NUMBER.eq(right))
              .or(work.YEAR.eq(right).and(work.NATURAL_NUMBER.eq(left)));
      if (!kind.isEmpty()) {
        condition = condition.and(substring(work.CELEX, 6, 1).eq(kind));
      }
      if (!subtype.isEmpty()) {
        condition =
            condition.and(
                work.RESOURCE_TYPE.endsWith("_" + subtype).or(work.RESOURCE_TYPE.eq(subtype)));
      }
      ids =
          catalogue
              .select(work.CELEX)
              .from(work)
              .where(condition)
              .limit(2)
              .fetch(row -> new ActIdentifier(row.value1()));
    } else {
      var alias = CATALOGUE_SEARCH_ALIAS;
      var work = CATALOGUE_SEARCH_WORK;
      ids =
          catalogue
              .select(alias.CELEX)
              .from(alias)
              .join(work)
              .on(work.CELEX.eq(alias.CELEX))
              .where(
                  alias.ALIAS_NORM.eq(
                      typed.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")))
              .and(alias.IS_EXACT_REDIRECT)
              .fetch(row -> new ActIdentifier(row.value1()));
    }
    return ids.size() == 1 ? Optional.of(ids.getFirst()) : Optional.empty();
  }

  @Override
  public Found matching(Query query) {
    var observation =
        Observation.createNotStarted("catalogue.search.match", observations)
            .lowCardinalityKeyValue("kind", query.kind().code())
            .lowCardinalityKeyValue("backend", "undetermined")
            .lowCardinalityKeyValue("result", "unknown");
    return observation.observe(() -> match(query, observation));
  }

  private Found match(Query query, Observation observation) {
    if (!Boolean.TRUE.equals(
        db.queryForObject(
            "select completed_at is not null from catalogue_search_rebuild", Boolean.class))) {
      observation.lowCardinalityKeyValue("backend", "cellar");
      LOG.debug("Searching Cellar while the first search index is pending");
      var found = cellar.matching(query);
      observation.lowCardinalityKeyValue("result", found.acts().isEmpty() ? "empty" : "title");
      return found;
    }
    observation.lowCardinalityKeyValue("backend", "postgres");
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              db.execute("set local pg_trgm.word_similarity_threshold = 0.60");
              return local(query, observation);
            }));
  }

  private Found local(Query query, Observation observation) {
    var kind =
        switch (query.kind()) {
          case REGULATION -> "R";
          case DIRECTIVE -> "L";
          case DECISION -> "D";
          case ALL, AMENDING -> query.kind().code();
        };
    ResultSetExtractor<Found> page = rows -> read(rows, query.size(), observation);
    return Objects.requireNonNull(
        db.query(
            SEARCH,
            page,
            terms(query),
            kind,
            query.words().size() <= 3 && query.typed().length() <= 28,
            (long) query.size() + 1,
            query.offset()));
  }

  private static String terms(Query query) {
    // Preserve slashes inside citations, discarding punctuation outside the first ten words.
    var lower = query.typed().toLowerCase(Locale.ROOT);
    var words = SEARCH_WORD.matcher(lower).results().limit(Search.Query.MOST_WORDS).toList();
    if (words.isEmpty()) return "";
    return lower
        .substring(words.getFirst().start(), words.getLast().end())
        .replaceAll("[^\\p{L}\\p{N}/]+", " ");
  }

  private static Found read(ResultSet rows, int limit, Observation observation)
      throws SQLException {
    List<Match> acts = new ArrayList<>();
    boolean more = rows.next();
    String result = more ? (rows.getBoolean("typo") ? "typo" : "lexical") : "empty";
    while (more && acts.size() < limit) {
      var id = new ActIdentifier(rows.getString("celex"));
      var title = rows.getString("title");
      var eli = Optional.ofNullable(rows.getString("eli")).flatMap(Eli::parse);
      var type = Optional.ofNullable(rows.getString("resource_type")).map(ResourceType::ofUri);
      boolean inForce = rows.getBoolean("in_force");
      List<Amends> amends = new ArrayList<>();
      do {
        var target = rows.getString("amends");
        if (target != null) {
          var targetEli = Optional.ofNullable(rows.getString("amends_eli")).flatMap(Eli::parse);
          amends.add(new Amends(new ActIdentifier(target), targetEli.orElse(null)));
        }
        more = rows.next();
      } while (more && id.celex().equals(rows.getString("celex")));
      acts.add(new Match(id, eli.orElse(null), type.orElse(null), title, inForce, amends));
    }
    observation.lowCardinalityKeyValue("result", result);
    LOG.debug("Postgres search returned {} acts ({}, more={})", acts.size(), result, more);
    return new Found(acts, more);
  }
}
