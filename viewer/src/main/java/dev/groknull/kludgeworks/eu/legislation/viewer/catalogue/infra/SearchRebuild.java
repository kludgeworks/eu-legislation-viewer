// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_AMENDMENT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXPRESSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_AMENDMENT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_CITATION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_CONCEPT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_LABEL;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_SHORT_TITLE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_TITLE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_VOCABULARY;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_EXTRACT_WORK;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_REBUILD;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_STAGE_AMENDMENT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_STAGE_EXPRESSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_STAGE_WORK;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CATALOGUE_SEARCH_WORK;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Routines;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Builds the English index of acts from Cellar, and publishes it. Every viewer checks each hour,
 * from an hour after it starts. One viewer builds at a time.
 *
 * <ol>
 *   <li>The build takes a session advisory lock on its own connection. A second viewer, or a second
 *       tick, finds the lock taken and skips.
 *   <li>It claims the build in {@code catalogue_search_rebuild}, and commits the claim before it
 *       asks Cellar. A build is due after 02:00 UTC each day. After a failed claim, the next is 24
 *       hours later.
 *   <li>It copies Cellar's extracts into the unlogged {@code catalogue_search_extract_*} tables.
 *       Each copy commits by itself. The functions {@code catalogue_search_stage_vocabulary} and
 *       {@code catalogue_search_stage_partition} of the migration turn them into the rows of the
 *       next index, in {@code catalogue_search_stage_*}, one partition of years at a time.
 *   <li>It publishes the staged rows in one short transaction, if they hold at least 95% of the
 *       acts of the index. Readers see the previous index until then.
 * </ol>
 */
@Component
class SearchRebuild {
  private static final Logger LOG = LoggerFactory.getLogger(SearchRebuild.class);

  /** The key of the advisory lock: the identifier of the table of the build. */
  private static final Field<Long> LOCK =
      field("{0}::regclass::oid::bigint", SQLDataType.BIGINT, inline("catalogue_search_rebuild"));

  /** The time of day after which a new build is due. */
  private static final LocalTime DUE = LocalTime.of(2, 0);

  /** How long after a failed claim the next claim waits. */
  private static final Duration BACK_OFF = Duration.ofHours(24);

  /** Nine partitions of years that cover all years without overlap, future decades too. */
  private static final List<String> YEARS =
      List.of(
          "(?:0...|1[0-8]..|19[0-7].)",
          "198.",
          "199[0-4]",
          "199[5-9]",
          "200[0-4]",
          "200[5-9]",
          "201[0-4]",
          "201[5-9]",
          "(?:20[2-9].|2[1-9]..|[3-9]...)");

  /** The tables that a build fills, and empties when it starts and ends. */
  private static final List<Table<?>> STAGING =
      List.of(
          CATALOGUE_SEARCH_EXTRACT_WORK,
          CATALOGUE_SEARCH_EXTRACT_TITLE,
          CATALOGUE_SEARCH_EXTRACT_CONCEPT,
          CATALOGUE_SEARCH_EXTRACT_AMENDMENT,
          CATALOGUE_SEARCH_EXTRACT_CITATION,
          CATALOGUE_SEARCH_EXTRACT_LABEL,
          CATALOGUE_SEARCH_EXTRACT_SHORT_TITLE,
          CATALOGUE_SEARCH_EXTRACT_VOCABULARY,
          CATALOGUE_SEARCH_STAGE_WORK,
          CATALOGUE_SEARCH_STAGE_AMENDMENT,
          CATALOGUE_SEARCH_STAGE_EXPRESSION);

  private final ObservationRegistry observations;
  private final DataSource database;
  private final Sparql cellar;
  private final Clock clock;

  SearchRebuild(DataSource database, Sparql cellar, ObservationRegistry observations, Clock clock) {
    this.observations = observations;
    this.database = database;
    this.cellar = cellar;
    this.clock = clock;
  }

  @Scheduled(
      initialDelayString = "${viewer.catalogue.search.start-delay:1h}",
      fixedRateString = "${viewer.catalogue.search.check-every:1h}")
  void tick() {
    try (var job = MDC.putCloseable("job", "search-rebuild")) {
      long start = System.nanoTime();
      try {
        rebuildNow();
      } catch (RuntimeException | SQLException failure) {
        // Logged here once, with the job's MDC: the scheduler does not log it again.
        LOG.warn(
            "Search rebuild failed after {} ms",
            Duration.ofNanos(System.nanoTime() - start).toMillis(),
            failure);
      }
    }
  }

  /**
   * Builds and publishes the index if a build is due and no other viewer builds it. Throws what
   * made the build fail; {@link #tick} logs it.
   */
  void rebuildNow() throws SQLException {
    var run =
        Observation.createNotStarted("catalogue.search.rebuild", observations)
            .lowCardinalityKeyValue("outcome", "running");
    run.observeChecked(() -> runOnce(run));
  }

  private void runOnce(Observation run) throws SQLException {
    long start = System.nanoTime();
    try (var connection = database.getConnection()) {
      connection.setAutoCommit(true);
      long acts = rebuild(connection);
      if (acts == 0) {
        run.lowCardinalityKeyValue("outcome", "skipped");
        LOG.debug("Skipped search rebuild: no run claimed");
      } else {
        run.lowCardinalityKeyValue("outcome", "published");
        LOG.info(
            "Published search index with {} acts in {} ms",
            acts,
            Duration.ofNanos(System.nanoTime() - start).toMillis());
      }
    } catch (RuntimeException | SQLException failure) {
      run.lowCardinalityKeyValue("outcome", "failed");
      throw failure;
    }
  }

  /** Builds and publishes the index on {@code connection}. Returns how many acts, or 0 if none. */
  private long rebuild(Connection connection) throws SQLException {
    // The lock is the session's: it holds across the commits of the copies, and Postgres releases
    // it if the connection dies.
    var db = DSL.using(connection, SQLDialect.POSTGRES);
    if (!Boolean.TRUE.equals(
        db.select(function("pg_try_advisory_lock", SQLDataType.BOOLEAN, LOCK)).fetchOne(0))) {
      return 0;
    }
    try (var session = new RebuildSession(connection, db)) {
      if (!claim(db)) {
        return 0;
      }
      session.staging = true;
      empty(db);
      stage(connection, db);
      return db.transactionResult(published -> publish(published.dsl()));
    }
  }

  /**
   * Claims the build, if one is due and no failed claim is recent. A build is due once a day, after
   * 02:00 UTC.
   */
  private boolean claim(DSLContext db) {
    var now = clock.instant();
    var rebuild = CATALOGUE_SEARCH_REBUILD;
    return db.update(rebuild)
            .set(rebuild.CLAIMED_AT, now)
            .where(rebuild.ONLY_ROW)
            .and(rebuild.COMPLETED_AT.isNull().or(rebuild.COMPLETED_AT.lt(lastDue(now))))
            .and(
                rebuild
                    .CLAIMED_AT
                    .le(rebuild.COMPLETED_AT)
                    .or(rebuild.CLAIMED_AT.le(now.minus(BACK_OFF))))
            .execute()
        > 0;
  }

  /** Returns the last time at or before {@code now} from which a build is due. */
  static Instant lastDue(Instant now) {
    var today = now.atOffset(ZoneOffset.UTC).toLocalDate();
    var due = today.atTime(DUE).toInstant(ZoneOffset.UTC);
    return due.isAfter(now) ? due.minus(Duration.ofDays(1)) : due;
  }

  /** Releases the lock of a build, and empties what it staged. */
  private static final class RebuildSession implements AutoCloseable {
    private final Connection connection;
    private final DSLContext db;
    private boolean staging;

    RebuildSession(Connection connection, DSLContext db) {
      this.connection = connection;
      this.db = db;
    }

    @Override
    public void close() throws SQLException {
      try {
        if (staging) {
          empty(db);
        }
        db.select(function("pg_advisory_unlock", SQLDataType.BOOLEAN, LOCK)).fetch();
      } catch (RuntimeException failure) {
        // A session with uncertain lock state must not return to the pool.
        connection.abort(Runnable::run);
        throw failure;
      }
    }
  }

  private static void empty(DSLContext db) {
    STAGING.forEach(table -> db.truncate(table).execute());
  }

  /** Copies Cellar's extracts, and stages the rows of the next index, partition by partition. */
  private void stage(Connection connection, DSLContext db) throws SQLException {
    LOG.info("Building the search index from Cellar");
    for (Extract extract : List.of(Extract.SKOS_EN, Extract.CITATIONS, Extract.SHORT_TITLES_EN)) {
      copy(connection, extract, extract.query);
    }
    Routines.catalogueSearchStageVocabulary(db.configuration());
    for (String years : YEARS) {
      for (Extract extract :
          List.of(Extract.WORKS, Extract.TITLES, Extract.CONCEPTS, Extract.AMENDS)) {
        copy(
            connection,
            extract,
            Sparql.fill(
                extract.query,
                Map.of("pattern", "^3" + years.replace(".", "[0-9]") + "[RLD][0-9]{4}$")));
      }
      Routines.catalogueSearchStagePartition(db.configuration());
    }
  }

  /**
   * Replaces the index with the staged rows, in the transaction of {@code db}, and records the
   * time. Throws if the staged acts are fewer than 95% of the acts of the index. Returns how many
   * acts the index has.
   */
  private long publish(DSLContext db) {
    var staged = CATALOGUE_SEARCH_STAGE_WORK;
    var work = CATALOGUE_SEARCH_WORK;
    long acts = db.fetchCount(staged);
    long previous = db.fetchCount(work);
    if (acts == 0 || acts * 100 < previous * 95) {
      throw new IllegalStateException(
          "Incomplete Cellar search inventory: "
              + acts
              + " acts staged, "
              + previous
              + " previously indexed");
    }

    db.deleteFrom(work)
        .where(notExists(selectOne().from(staged).where(staged.CELEX.eq(work.CELEX))))
        .execute();
    db.insertInto(
            work, work.CELEX, work.ELI, work.RESOURCE_TYPE, work.IN_FORCE, work.CITATION_COUNT)
        .select(
            select(
                    staged.CELEX,
                    staged.ELI,
                    staged.RESOURCE_TYPE,
                    staged.IN_FORCE,
                    staged.CITATION_COUNT)
                .from(staged))
        .onConflict(work.CELEX)
        .doUpdate()
        .set(work.ELI, excluded(work.ELI))
        .set(work.RESOURCE_TYPE, excluded(work.RESOURCE_TYPE))
        .set(work.IN_FORCE, excluded(work.IN_FORCE))
        .set(work.CITATION_COUNT, excluded(work.CITATION_COUNT))
        .where(
            row(work.ELI, work.RESOURCE_TYPE, work.IN_FORCE, work.CITATION_COUNT)
                .isDistinctFrom(
                    row(
                        excluded(work.ELI),
                        excluded(work.RESOURCE_TYPE),
                        excluded(work.IN_FORCE),
                        excluded(work.CITATION_COUNT))))
        .execute();

    var stagedAmendment = CATALOGUE_SEARCH_STAGE_AMENDMENT;
    var amendment = CATALOGUE_SEARCH_AMENDMENT;
    db.deleteFrom(amendment)
        .where(
            notExists(
                selectOne()
                    .from(stagedAmendment)
                    .where(stagedAmendment.ACT.eq(amendment.ACT))
                    .and(stagedAmendment.AMENDS.eq(amendment.AMENDS))))
        .execute();
    db.insertInto(amendment, amendment.ACT, amendment.AMENDS, amendment.AMENDS_ELI)
        .select(
            select(stagedAmendment.ACT, stagedAmendment.AMENDS, stagedAmendment.AMENDS_ELI)
                .from(stagedAmendment))
        .onConflict(amendment.ACT, amendment.AMENDS)
        .doUpdate()
        .set(amendment.AMENDS_ELI, excluded(amendment.AMENDS_ELI))
        .where(amendment.AMENDS_ELI.isDistinctFrom(excluded(amendment.AMENDS_ELI)))
        .execute();

    var stagedExpression = CATALOGUE_SEARCH_STAGE_EXPRESSION;
    var expression = CATALOGUE_SEARCH_EXPRESSION;
    db.insertInto(
            expression,
            expression.CELEX,
            expression.LANGUAGE_CODE,
            expression.TITLE,
            expression.SUBSTANTIVE_TITLE,
            expression.SHORT_TITLES_NORM,
            expression.SEARCH_TSV)
        .select(
            select(
                    stagedExpression.CELEX,
                    stagedExpression.LANGUAGE_CODE,
                    stagedExpression.TITLE,
                    stagedExpression.SUBSTANTIVE_TITLE,
                    stagedExpression.SHORT_TITLES_NORM,
                    stagedExpression.SEARCH_TSV)
                .from(stagedExpression))
        .onConflict(expression.CELEX, expression.LANGUAGE_CODE)
        .doUpdate()
        .set(expression.TITLE, excluded(expression.TITLE))
        .set(expression.SUBSTANTIVE_TITLE, excluded(expression.SUBSTANTIVE_TITLE))
        .set(expression.SHORT_TITLES_NORM, excluded(expression.SHORT_TITLES_NORM))
        .set(expression.SEARCH_TSV, excluded(expression.SEARCH_TSV))
        .where(
            row(
                    expression.TITLE,
                    expression.SUBSTANTIVE_TITLE,
                    expression.SHORT_TITLES_NORM,
                    expression.SEARCH_TSV)
                .isDistinctFrom(
                    row(
                        excluded(expression.TITLE),
                        excluded(expression.SUBSTANTIVE_TITLE),
                        excluded(expression.SHORT_TITLES_NORM),
                        excluded(expression.SEARCH_TSV))))
        .execute();

    var rebuild = CATALOGUE_SEARCH_REBUILD;
    db.update(rebuild).set(rebuild.COMPLETED_AT, clock.instant()).where(rebuild.ONLY_ROW).execute();
    return acts;
  }

  private void copy(Connection connection, Extract extract, String query) throws SQLException {
    var copy = connection.unwrap(PGConnection.class).getCopyAPI();
    Observation.createNotStarted("catalogue.search.extract", observations)
        .lowCardinalityKeyValue("family", extract.name().toLowerCase(Locale.ROOT))
        .observe(() ->
            cellar.copyCsv(
                query,
                input -> {
                  try {
                    long rows = copy.copyIn(extract.copy, input);
                    LOG.debug("Copied {} rows from Cellar search extract {}", rows, extract);
                    return rows;
                  } catch (SQLException failure) {
                    throw new IllegalStateException(
                        "Cannot load search extract " + extract, failure);
                  }
                }));
  }

  /**
   * One of Cellar's extracts: its SPARQL query, and the COPY that loads its CSV answer into its
   * table. jOOQ has no COPY. The CSV header must match the columns of the table.
   */
  private enum Extract {
    SKOS_EN(CATALOGUE_SEARCH_EXTRACT_LABEL),
    CITATIONS(CATALOGUE_SEARCH_EXTRACT_CITATION),
    SHORT_TITLES_EN(CATALOGUE_SEARCH_EXTRACT_SHORT_TITLE),
    WORKS(CATALOGUE_SEARCH_EXTRACT_WORK),
    TITLES(CATALOGUE_SEARCH_EXTRACT_TITLE),
    CONCEPTS(CATALOGUE_SEARCH_EXTRACT_CONCEPT),
    AMENDS(CATALOGUE_SEARCH_EXTRACT_AMENDMENT);

    final String query;
    final String copy;

    Extract(Table<?> table) {
      this.copy = "copy " + table.getName() + " from stdin with (format csv, header match)";
      this.query =
          Sparql.template(SearchRebuild.class, "index-" + name().toLowerCase(Locale.ROOT) + ".rq");
    }
  }
}
