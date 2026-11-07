// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Pending migrations on Postgres, in one container: each test in a database of its own, with its
 * migrations in a directory of its own, as a module's are.
 */
class PendingMigrationsTest {
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
  private static final AtomicInteger DATABASES = new AtomicInteger();

  @TempDir Path migrations;
  private String url = "";

  @BeforeAll
  static void start() {
    POSTGRES.start();
  }

  @AfterAll
  static void stop() {
    POSTGRES.stop();
  }

  @BeforeEach
  void database() throws SQLException {
    var name = "test_" + DATABASES.incrementAndGet();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      // PostgreSQL CREATE DATABASE does not accept parameterized identifiers; name is a safe atomic
      // integer
      // nosemgrep: semgrep.jdbc-no-raw-sql-concatenation
      statement.execute("create database " + name);
    }
    url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
  }

  @Test
  void runsPendingMigrationsAfterTheReleasedOnes() throws IOException {
    write("V2026_11_0_001__act.sql", "create table act (id int);");
    write("NEXT_002__title.sql", "alter table act add column title text;");
    write("NEXT_001__date.sql", "alter table act add column date date;");

    flyway().migrate();

    assertThat(applied())
        .containsExactly(
            "2026.11.0.001 V2026_11_0_001__act.sql",
            "9999.001 V9999_001__date.sql",
            "9999.002 V9999_002__title.sql");
  }

  @Test
  void followsAPendingMigrationToItsReleasedName() throws IOException {
    write("V2026_11_0_001__act.sql", "create table act (id int);");
    write("NEXT_001__title.sql", "alter table act add column title text;");
    flyway().migrate();

    rename("NEXT_001__title.sql", "V2026_12_0_001__title.sql");
    var result = flyway().migrate();

    assertThat(result.migrationsExecuted).isZero();
    assertThat(applied())
        .containsExactly(
            "2026.11.0.001 V2026_11_0_001__act.sql", "2026.12.0.001 V2026_12_0_001__title.sql");
  }

  @Test
  void followsAPendingMigrationNumberedAgain() throws IOException {
    write("NEXT_001__act.sql", "create table act (id int);");
    write("NEXT_003__title.sql", "alter table act add column title text;");
    flyway().migrate();

    rename("NEXT_003__title.sql", "NEXT_002__title.sql");
    flyway().migrate();

    assertThat(applied())
        .containsExactly("9999.001 V9999_001__act.sql", "9999.002 V9999_002__title.sql");
  }

  @Test
  void leavesAPendingMigrationChangedSinceItRanToFlyway() throws IOException {
    write("NEXT_001__act.sql", "create table act (id int);");
    flyway().migrate();

    rename("NEXT_001__act.sql", "V2026_12_0_001__act.sql");
    write("V2026_12_0_001__act.sql", "create table act (id bigint);");

    assertThatThrownBy(() -> flyway().migrate()).isInstanceOf(FlywayValidateException.class);
  }

  private Flyway flyway() {
    return PendingMigrations.configure(Flyway.configure())
        .callbacks(new RenamedMigrations())
        .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("filesystem:" + migrations)
        .load();
  }

  private void write(String name, String sql) throws IOException {
    Files.writeString(migrations.resolve(name), sql);
  }

  private void rename(String from, String to) throws IOException {
    Files.move(migrations.resolve(from), migrations.resolve(to));
  }

  /** Each migration in the schema history: its version and script. */
  private String[] applied() {
    return Arrays.stream(flyway().info().applied())
        .map(migration -> migration.getVersion().getVersion() + " " + migration.getScript())
        .toArray(String[]::new);
  }
}
