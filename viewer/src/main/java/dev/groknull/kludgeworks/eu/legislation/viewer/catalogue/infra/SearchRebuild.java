// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes the complete English index on one connection. Readers see the previous build until
 * commit.
 */
@Component
@ConditionalOnProperty(name = "viewer.catalogue.search.enabled", havingValue = "true")
class SearchRebuild {
  private static final Logger LOG = LoggerFactory.getLogger(SearchRebuild.class);
  private final ObservationRegistry observations;
  private final DataSource database;
  private final Sparql cellar;

  SearchRebuild(DataSource database, Sparql cellar, ObservationRegistry observations) {
    this.observations = observations;
    this.database = database;
    this.cellar = cellar;
  }

  @Scheduled(
      initialDelayString = "${viewer.catalogue.search.start-delay:10s}",
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

  private long rebuild(Connection connection) throws SQLException {
    try (var sql = connection.createStatement()) {
      // A session lock survives commits between extracts and is released if the connection dies.
      try (var lock =
          sql.executeQuery(
              "select pg_try_advisory_lock('catalogue_search_rebuild'::regclass::oid::bigint)")) {
        lock.next();
        if (!lock.getBoolean(1)) return 0;
      }
      try (var session = new RebuildSession(connection)) {
        // Commit the attempt before contacting Cellar. Failed attempts wait 24 hours;
        // successful builds are due again at the next nightly boundary.
        if (sql.executeUpdate(
                """
                update catalogue_search_rebuild set claimed_at=clock_timestamp()
                where only_row
                  and (completed_at is null or completed_at < date_bin(interval '1 day', now(), timestamptz '2000-01-01 02:00+00'))
                  and (claimed_at <= completed_at or claimed_at <= now() - interval '24 hours')
                """)
            == 0) return 0;
        session.staging = true;
        stage(connection, sql);
        connection.setAutoCommit(false);
        long acts = publish(sql);
        connection.commit();
        return acts;
      }
    }
  }

  private static final class RebuildSession implements AutoCloseable {
    private final Connection connection;
    private boolean staging;

    RebuildSession(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void close() throws SQLException {
      try (var sql = connection.createStatement()) {
        if (!connection.getAutoCommit()) {
          connection.rollback();
          connection.setAutoCommit(true);
        }
        if (staging) {
          sql.execute(
              """
              drop table if exists pg_temp.works, pg_temp.titles, pg_temp.concepts, pg_temp.amends,
                pg_temp.citations, pg_temp.skos_en, pg_temp.short_titles_en, pg_temp.labels,
                pg_temp.tmp_work, pg_temp.tmp_amendment, pg_temp.tmp_expression
              """);
        }
        sql.execute("select pg_advisory_unlock('catalogue_search_rebuild'::regclass::oid::bigint)");
      } catch (SQLException failure) {
        // A session with uncertain lock or staging state must not return to the pool.
        connection.abort(Runnable::run);
        throw failure;
      }
    }
  }

  private void stage(Connection connection, Statement sql) throws SQLException {
    LOG.info("Building the search index from Cellar");
    sql.execute(SearchSql.read("index-stage.sql"));
    for (Extract extract : List.of(Extract.SKOS_EN, Extract.CITATIONS, Extract.SHORT_TITLES_EN)) {
      copy(connection, extract, extract.query);
    }
    sql.execute(SearchSql.read("index-vocabulary.sql"));
    // Nine non-overlapping partitions cover all years, including future decades.
    for (String years :
        List.of(
            "(?:0...|1[0-8]..|19[0-7].)",
            "198.",
            "199[0-4]",
            "199[5-9]",
            "200[0-4]",
            "200[5-9]",
            "201[0-4]",
            "201[5-9]",
            "(?:20[2-9].|2[1-9]..|[3-9]...)")) {
      for (Extract extract :
          List.of(Extract.WORKS, Extract.TITLES, Extract.CONCEPTS, Extract.AMENDS)) {
        var query =
            Sparql.fill(
                extract.query,
                Map.of("pattern", "^3" + years.replace(".", "[0-9]") + "[RLD][0-9]{4}$"));
        copy(connection, extract, query);
      }
      sql.execute(SearchSql.read("index-partition.sql"));
    }
  }

  private static long publish(Statement sql) throws SQLException {
    long acts;
    try (var counts =
        sql.executeQuery(
            """
            select count(*), (select count(*) from catalogue_search_work)
            from tmp_work
            """)) {
      counts.next();
      acts = counts.getLong(1);
      long previous = counts.getLong(2);
      if (acts == 0 || acts * 100 < previous * 95) {
        throw new IllegalStateException(
            "Incomplete Cellar search inventory: "
                + acts
                + " acts staged, "
                + previous
                + " previously indexed");
      }
    }
    sql.execute(SearchSql.read("index-publish.sql"));
    return acts;
  }

  private void copy(Connection connection, Extract extract, String query) throws SQLException {
    var copy = connection.unwrap(PGConnection.class).getCopyAPI();
    Observation.createNotStarted("catalogue.search.extract", observations)
        .lowCardinalityKeyValue("family", extract.name().toLowerCase(Locale.ROOT))
        .observe(
            () ->
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

  private enum Extract {
    SKOS_EN("copy pg_temp.skos_en from stdin with (format csv, header match)"),
    CITATIONS("copy pg_temp.citations from stdin with (format csv, header match)"),
    SHORT_TITLES_EN("copy pg_temp.short_titles_en from stdin with (format csv, header match)"),
    WORKS("copy pg_temp.works from stdin with (format csv, header match)"),
    TITLES("copy pg_temp.titles from stdin with (format csv, header match)"),
    CONCEPTS("copy pg_temp.concepts from stdin with (format csv, header match)"),
    AMENDS("copy pg_temp.amends from stdin with (format csv, header match)");

    final String query;
    final String copy;

    Extract(String copy) {
      this.copy = copy;
      this.query =
          Sparql.template(SearchRebuild.class, "index-" + name().toLowerCase(Locale.ROOT) + ".rq");
    }
  }
}
