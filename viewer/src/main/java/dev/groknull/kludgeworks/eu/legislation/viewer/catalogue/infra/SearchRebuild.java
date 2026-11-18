// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
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
  private final DataSource database;
  private final Sparql cellar;

  SearchRebuild(DataSource database, Sparql cellar) {
    this.database = database;
    this.cellar = cellar;
  }

  @Scheduled(
      initialDelayString = "${viewer.catalogue.search.start-delay:10s}",
      fixedRateString = "${viewer.catalogue.search.check-every:1h}")
  void tick() throws SQLException {
    try (var connection = database.getConnection()) {
      connection.setAutoCommit(false);
      try {
        rebuild(connection);
        connection.commit();
      } catch (RuntimeException | SQLException failure) {
        connection.rollback();
        throw failure;
      }
    }
  }

  private void rebuild(Connection connection) throws SQLException {
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
        return;
      }
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
      try (var counts =
          sql.executeQuery(
              """
              select count(*) > 0 and count(*) >= 0.95 * (select count(*) from catalogue_search_work)
              from tmp_work
              """)) {
        counts.next();
        if (!counts.getBoolean(1)) {
          throw new IllegalStateException("Incomplete Cellar search inventory");
        }
      }
      sql.execute(resource("index-publish.sql"));
    }
  }

  private void copy(Connection connection, String family, String query) throws SQLException {
    var copy = connection.unwrap(PGConnection.class).getCopyAPI();
    cellar.copyCsv(
        query,
        input -> {
          try {
            return copy.copyIn(
                "copy pg_temp." + family + " from stdin with (format csv, header match)", input);
          } catch (SQLException failure) {
            throw new IllegalStateException("Cannot load search extract " + family, failure);
          }
        });
  }

  private static String resource(String name) {
    return Sparql.template(SearchRebuild.class, name);
  }
}
