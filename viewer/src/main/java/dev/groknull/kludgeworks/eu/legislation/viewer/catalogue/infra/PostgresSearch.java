// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_AMENDMENT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXPRESSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_REBUILD;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_WORK;
import static dev.groknull.kludgeworks.jooq.postgres.TextSearch.matches;
import static dev.groknull.kludgeworks.jooq.postgres.TextSearch.plainToTsQuery;
import static dev.groknull.kludgeworks.jooq.postgres.TextSearch.rankCd;
import static dev.groknull.kludgeworks.jooq.postgres.TextSearch.wordSimilar;
import static org.jooq.impl.DSL.concat;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.ln;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.substring;
import static org.jooq.impl.DSL.unquotedName;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.when;
import static org.jooq.impl.SQLDataType.VARCHAR;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Citation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Searches;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Routines;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.CatalogueSearchAmendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.jooq.postgres.TsQuery;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record6;
import org.jooq.Record8;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
class PostgresSearch implements Searches {
  private static final Logger LOG = LoggerFactory.getLogger(PostgresSearch.class);

  /**
   * The text search configuration that the index of acts is built with. It must be the one of the
   * migration that builds {@code search_tsv}, so it is not a property of {@link SearchTuning}.
   */
  private static final String ENGLISH = "english";

  // The columns of the matches, as the subqueries of the search name them.
  private static final Field<ActIdentifier> CELEX =
      field(name("celex"), CATALOGUE_SEARCH_WORK.CELEX.getDataType());
  private static final Field<Boolean> IN_FORCE = field(name("in_force"), SQLDataType.BOOLEAN);
  private static final Field<Boolean> AMENDING = field(name("amending"), SQLDataType.BOOLEAN);
  private static final Field<Boolean> EXACT_NUMBER =
      field(name("exact_number"), SQLDataType.BOOLEAN);
  private static final Field<Boolean> TYPO = field(name("typo"), SQLDataType.BOOLEAN);
  private static final Field<Float> SCORE = field(name("score"), SQLDataType.REAL);
  private static final Pattern SEARCH_WORD = Pattern.compile("[\\p{L}\\p{N}]+");
  private final DSLContext catalogue;
  private final CellarSearch cellar;
  private final TransactionOperations transactions;

  private final ObservationRegistry observations;
  private final SearchTuning tuning;

  PostgresSearch(
      DSLContext catalogue,
      Sparql sparql,
      TransactionOperations transactions,
      ObservationRegistry observations,
      SearchTuning tuning) {
    this.tuning = tuning;
    this.observations = observations;
    this.catalogue = catalogue;
    this.transactions = transactions;
    this.cellar = new CellarSearch(sparql);
  }

  @Override
  public Optional<ActIdentifier> identified(String typed) {
    var citation = Citation.read(typed);
    if (citation.isEmpty()) {
      return Optional.empty();
    }
    return Observation.createNotStarted("catalogue.search.identify", observations)
        .observe(() -> identify(citation.get()));
  }

  /**
   * Returns the one act that {@code citation} names, if it names exactly one: of the acts whose
   * year and number are its two numbers, in either order ({@link Search#aCitationGoesToOneAct}).
   */
  private Optional<ActIdentifier> identify(Citation citation) {
    var work = CATALOGUE_SEARCH_WORK;
    var condition =
        work.ACT_YEAR
            .eq(citation.first())
            .and(work.NATURAL_NUMBER.eq(citation.second()))
            .or(work.ACT_YEAR.eq(citation.second()).and(work.NATURAL_NUMBER.eq(citation.first())));
    var candidates =
        catalogue
            .select(work.CELEX, work.RESOURCE_TYPE)
            .from(work)
            .where(condition)
            .fetch(row ->
                new Citation.Candidate(
                    row.value1(), row.value2() == null ? null : ResourceType.ofUri(row.value2())));
    return Search.aCitationGoesToOneAct(citation, candidates);
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
    var rebuild = CATALOGUE_SEARCH_REBUILD;
    if (!catalogue.fetchExists(rebuild, rebuild.COMPLETED_AT.isNotNull())) {
      observation.lowCardinalityKeyValue("backend", "cellar");
      LOG.debug("Searching Cellar while the first search index is pending");
      var found = cellar.matching(query);
      observation.lowCardinalityKeyValue("result", found.acts().isEmpty() ? "empty" : "title");
      return found;
    }
    observation.lowCardinalityKeyValue("backend", "postgres");
    return Objects.requireNonNull(
        transactions.execute(status -> {
          catalogue
              .setLocal(
                  unquotedName("pg_trgm", "word_similarity_threshold"),
                  inline(tuning.typoThreshold()))
              .execute();
          return read(local(query), query.size(), observation);
        }));
  }

  /**
   * Returns the rows of one page of the acts that match {@code query}, and of one act more. An act
   * has one row for each act that it amends.
   *
   * <ol>
   *   <li>An act matches if its words match ({@code @@}). Its score is how close the words are in
   *       its title ({@code ts_rank_cd}), more for an act that more acts cite, and less for an act
   *       not in force.
   *   <li>Only if no act matches, and the query is short, an act matches if the typed words are
   *       like a run of words of its title ({@code <%}). Its score is that likeness.
   *   <li>The order is: the act whose number was typed, the score, an act that amends no act, an
   *       act in force, and the newest CELEX. An act whose state Cellar does not give is not in
   *       force for the order: {@code is not distinct from true}, as {@code = true} would put it
   *       first.
   * </ol>
   */
  private Result<
          Record8<Boolean, ActIdentifier, String, String, Boolean, String, ActIdentifier, String>>
      local(Query query) {
    var typed = terms(query);
    var allowTypos = query.words().size() <= 3 && query.typed().length() <= 28;
    var words = plainToTsQuery(ENGLISH, Routines.unaccent2(typed));

    // Each use is its own subquery, so that Postgres plans it with the filter of that use.
    var lexical = name("lexical").as(scored(query, typed, false, words));
    Select<Record> candidates =
        allowTypos
            ? select(lexical.asterisk())
                .from(lexical)
                .unionAll(
                    scored(query, typed, true, words).and(notExists(selectOne().from(lexical))))
            : select(lexical.asterisk()).from(lexical);
    var all = candidates.asTable("candidates");
    var celex = column(all, CELEX);
    var exact = column(all, EXACT_NUMBER);
    var score = column(all, SCORE);
    var amending = column(all, AMENDING);
    var inForce = column(all, IN_FORCE);
    var page =
        select(all.asterisk())
            .from(all)
            .orderBy(
                exact.desc(),
                score.desc(),
                amending.asc(),
                field(inForce.isNotDistinctFrom(true)).desc(),
                celex.desc())
            .limit(query.size() + 1)
            .offset(query.offset())
            .asTable("page");

    var work = CATALOGUE_SEARCH_WORK;
    var expression = CATALOGUE_SEARCH_EXPRESSION;
    var amendment = CATALOGUE_SEARCH_AMENDMENT;
    var act = column(page, CELEX);
    return catalogue
        .with(lexical)
        .select(
            column(page, TYPO),
            act,
            work.ELI,
            work.RESOURCE_TYPE,
            column(page, IN_FORCE),
            expression.TITLE,
            amendment.AMENDS,
            amendment.AMENDS_ELI)
        .from(page)
        .join(work)
        .on(work.CELEX.eq(act))
        .join(expression)
        .on(expression.CELEX.eq(act), expression.LANGUAGE_CODE.eq(Language.ENG))
        .leftJoin(amendment)
        .on(amendment.ACT.eq(act))
        .orderBy(
            column(page, EXACT_NUMBER).desc(),
            column(page, SCORE).desc(),
            column(page, AMENDING).asc(),
            field(column(page, IN_FORCE).isNotDistinctFrom(true)).desc(),
            act.desc(),
            amendment.AMENDS)
        .fetch();
  }

  /**
   * Returns the acts of the kind of {@code query} that match {@code words}, with their score: by
   * their words, or, for {@code typo}, by the likeness of {@code typed} to their title.
   */
  private SelectConditionStep<Record6<ActIdentifier, Boolean, Boolean, Boolean, Boolean, Float>>
      scored(Query query, String typed, boolean typo, Field<TsQuery> words) {
    var work = CATALOGUE_SEARCH_WORK;
    var expression = CATALOGUE_SEARCH_EXPRESSION;
    var amendment = CATALOGUE_SEARCH_AMENDMENT;
    var cited =
        inline(BigDecimal.ONE).plus(ln(inline(new BigDecimal("1.0")).plus(work.CITATION_COUNT)));
    var score =
        typo
            ? Routines.wordSimilarity(val(typed), expression.SUBSTANTIVE_TITLE).times(cited)
            : rankCd(tuning.weights().rank(), expression.SEARCH_TSV, words, 32)
                .times(cited)
                .times(
                    when(work.IN_FORCE.isFalse(), inline(tuning.notInForce()))
                        .otherwise(inline(BigDecimal.ONE)));
    var number =
        concat(work.ACT_YEAR.cast(VARCHAR), inline("/"), work.NATURAL_NUMBER.cast(VARCHAR));
    var reversed =
        concat(work.NATURAL_NUMBER.cast(VARCHAR), inline("/"), work.ACT_YEAR.cast(VARCHAR));
    return select(
            work.CELEX.as(CELEX),
            work.IN_FORCE.as(IN_FORCE),
            field(exists(selectOne().from(amendment).where(amendment.ACT.eq(work.CELEX))))
                .as(AMENDING),
            field(val(typed).in(number, reversed)).as(EXACT_NUMBER),
            inline(typo).as(TYPO),
            score.as(SCORE))
        .from(expression)
        .join(work)
        .on(work.CELEX.eq(expression.CELEX))
        .where(expression.LANGUAGE_CODE.eq(Language.ENG))
        .and(kind(query.kind(), work.CELEX, amendment))
        .and(
            typo
                ? wordSimilar(val(typed), expression.SUBSTANTIVE_TITLE)
                : matches(expression.SEARCH_TSV, words));
  }

  /** Returns the column {@code like} of the subquery {@code table}. */
  private static <T> Field<T> column(Table<?> table, Field<T> like) {
    return Objects.requireNonNull(table.field(like), () -> table.getName() + " has no " + like);
  }

  /** Returns the condition that an act of {@code celex} is of {@code kind}. */
  private static Condition kind(
      Searches.Kind kind, Field<ActIdentifier> celex, CatalogueSearchAmendment amendment) {
    return switch (kind) {
      case ALL -> noCondition();
      case REGULATION -> sector(celex).eq("R");
      case DIRECTIVE -> sector(celex).eq("L");
      case DECISION -> sector(celex).eq("D");
      case AMENDING -> exists(selectOne().from(amendment).where(amendment.ACT.eq(celex)));
    };
  }

  /** Returns the letter of the kind of act in {@code celex}: R, L or D. */
  private static Field<String> sector(Field<ActIdentifier> celex) {
    return substring(celex.coerce(SQLDataType.CLOB), 6, 1);
  }

  private static String terms(Query query) {
    // Preserve slashes inside citations, discarding punctuation outside the first ten words.
    var lower = query.typed().toLowerCase(Locale.ROOT);
    var words = SEARCH_WORD.matcher(lower).results().limit(Searches.Query.MOST_WORDS).toList();
    if (words.isEmpty()) return "";
    return lower
        .substring(words.getFirst().start(), words.getLast().end())
        .replaceAll("[^\\p{L}\\p{N}/]+", " ");
  }

  private static Found read(
      List<
              ? extends
                  Record8<
                      Boolean,
                      ActIdentifier,
                      String,
                      String,
                      Boolean,
                      String,
                      ActIdentifier,
                      String>>
          rows,
      int limit,
      Observation observation) {
    List<Match> acts = new ArrayList<>();
    var result =
        rows.isEmpty()
            ? "empty"
            : Boolean.TRUE.equals(rows.getFirst().value1()) ? "typo" : "lexical";
    var at = 0;
    while (at < rows.size() && acts.size() < limit) {
      var first = rows.get(at);
      var id = first.value2();
      List<Amends> amends = new ArrayList<>();
      while (at < rows.size() && rows.get(at).value2().equals(id)) {
        var row = rows.get(at++);
        if (row.value7() != null) {
          amends.add(
              new Amends(
                  row.value7(),
                  Optional.ofNullable(row.value8()).flatMap(Eli::parse).orElse(null)));
        }
      }
      acts.add(
          new Match(
              id,
              Optional.ofNullable(first.value3()).flatMap(Eli::parse).orElse(null),
              Optional.ofNullable(first.value4()).map(ResourceType::ofUri).orElse(null),
              first.value6(),
              Boolean.TRUE.equals(first.value5()),
              amends));
    }
    var more = at < rows.size();
    observation.lowCardinalityKeyValue("result", result);
    LOG.debug("Postgres search returned {} acts ({}, more={})", acts.size(), result, more);
    return new Found(acts, more);
  }
}
