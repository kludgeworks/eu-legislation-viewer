// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
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
  void tick() throws SQLException {
    try (var job = MDC.putCloseable("job", "search-rebuild")) {
      var run =
          Observation.createNotStarted("catalogue.search.rebuild", observations)
              .lowCardinalityKeyValue("outcome", "running");
      run.observeChecked(() -> runOnce(run));
    }
  }

  private void runOnce(Observation run) throws SQLException {
    long start = System.nanoTime();
    try (var connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        long acts = rebuild(connection);
        connection.commit();
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
        connection.rollback();
        throw failure;
      }
    } catch (RuntimeException | SQLException failure) {
      run.lowCardinalityKeyValue("outcome", "failed");
      LOG.warn(
          "Search rebuild failed after {} ms: {}",
          Duration.ofNanos(System.nanoTime() - start).toMillis(),
          failure.toString());
      throw failure;
    }
  }

  private long rebuild(Connection connection) throws SQLException {
    try (var sql = connection.createStatement()) {
      // Only this row is locked during extraction. A crash releases the claim with the session.
      if (sql.executeUpdate(
              """
              update catalogue_search_rebuild set claimed_at=clock_timestamp()
              where only_row in (select only_row from catalogue_search_rebuild
                where completed_at is null or completed_at < date_bin(interval '1 day', now(), timestamptz '2000-01-01 02:00+00')
                for update skip locked)
              """)
          == 0) {
        return 0;
      }
      LOG.info("Building the search index from Cellar");
      sql.execute(resource("index-stage.sql"));
      for (String family : List.of("skos_en", "citations", "short_titles_en")) {
        copy(connection, family, resource("index-" + family + ".rq"));
      }
      sql.execute(resource("index-vocabulary.sql"));
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
        for (String family : List.of("works", "titles", "concepts", "amends")) {
          var query =
              Sparql.fill(
                  resource("index-" + family + ".rq"),
                  Map.of("pattern", "^3" + years.replace(".", "[0-9]") + "[RLD][0-9]{4}$"));
          copy(connection, family, query);
        }
        sql.execute(resource("index-partition.sql"));
      }
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
      sql.execute(resource("index-publish.sql"));
      return acts;
    }
  }

  private void copy(Connection connection, String family, String query) throws SQLException {
    var copy = connection.unwrap(PGConnection.class).getCopyAPI();
    LOG.debug("Loading Cellar search extract {}", family);
    cellar.copyCsv(
        query,
        input -> {
          try {
            long rows =
                copy.copyIn(
                    "copy pg_temp." + family + " from stdin with (format csv, header match)",
                    input);
            LOG.debug("Copied {} rows from Cellar search extract {}", rows, family);
            return rows;
          } catch (SQLException failure) {
            throw new IllegalStateException("Cannot load search extract " + family, failure);
          }
        });
  }

  private static String resource(String name) {
    return Sparql.template(SearchRebuild.class, name);
  }
}
