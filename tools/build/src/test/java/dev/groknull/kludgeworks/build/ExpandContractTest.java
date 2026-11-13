// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.build.postgres.Postgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The expand-contract check on Postgres, in Docker: each test with its migrations in a module of
 * its own, on a database of its own. The released schema is a table {@code act}, made by a V file.
 */
class ExpandContractTest {
  private static final AtomicInteger DATABASES = new AtomicInteger();
  private static @Nullable PostgreSQLContainer postgres;

  @TempDir Path migrations;

  @BeforeAll
  static void start() {
    postgres = Postgres.start();
  }

  @AfterAll
  static void stop() {
    if (postgres != null) {
      postgres.close();
    }
  }

  @Test
  void passesAnAddedColumn() throws Exception {
    released("create table act (celex text not null, title text);");
    pending("NEXT_001__act_date.sql", "alter table act add column dated date;");

    assertThat(check().problems()).isEmpty();
  }

  @Test
  void failsAColumnDroppedWithoutBeingDeprecated() throws Exception {
    released("create table act (celex text not null, title text);");
    pending("NEXT_001__act_title.sql", "alter table act drop column title;");

    var report = check();

    assertThat(report.passes()).isFalse();
    assertThat(report.problems()).singleElement().asString().contains("column act.title");
  }

  @Test
  void failsAColumnDeprecatedAndDroppedInOneRelease() throws Exception {
    released("create table act (celex text not null, title text);");
    pending(
        "NEXT_001__act_title_deprecated.sql", "comment on column act.title is 'deprecated: x';");
    pending("NEXT_002__act_title_dropped.sql", "alter table act drop column title;");

    assertThat(check().problems()).singleElement().asString().contains("column act.title");
  }

  @Test
  void passesAColumnDroppedAReleaseAfterItsDeprecation() throws Exception {
    released(
        "create table act (celex text not null, title text);",
        "comment on column act.title is 'deprecated: use act_title';");
    pending("NEXT_001__act_title_dropped.sql", "alter table act drop column title;");

    assertThat(check().problems()).isEmpty();
  }

  @Test
  void failsARenamedColumnAsTheOldNameGone() throws Exception {
    released("create table act (celex text not null, title text);");
    pending("NEXT_001__act_name.sql", "alter table act rename column title to name;");

    assertThat(check().problems())
        .singleElement()
        .asString()
        .contains("column act.title is dropped or renamed");
  }

  @Test
  void failsAChangedType() throws Exception {
    released("create table act (celex text not null, pages int);");
    pending("NEXT_001__act_pages.sql", "alter table act alter column pages type bigint;");

    assertThat(check().problems())
        .singleElement()
        .asString()
        .contains("act.pages changes type from integer to bigint");
  }

  @Test
  void failsADeprecatedColumnThatIsStillRequired() throws Exception {
    released("create table act (celex text not null, title text not null);");
    pending(
        "NEXT_001__act_title_deprecated.sql", "comment on column act.title is 'deprecated: x';");

    assertThat(check().problems())
        .singleElement()
        .asString()
        .contains("act.title is deprecated but not null without a default");
  }

  @Test
  void passesADeprecatedColumnWithADefault() throws Exception {
    released("create table act (celex text not null, title text not null);");
    pending(
        "NEXT_001__act_title_deprecated.sql",
        "alter table act alter column title set default '';",
        "comment on column act.title is 'deprecated: x';");

    assertThat(check().problems()).isEmpty();
  }

  @Test
  void failsATableDroppedWithoutBeingDeprecated() throws Exception {
    released("create table act (celex text not null);", "create table draft (id int);");
    pending("NEXT_001__draft_dropped.sql", "drop table draft;");

    assertThat(check().problems()).singleElement().asString().contains("table draft");
  }

  @Test
  void passesATableDroppedAReleaseAfterItsDeprecation() throws Exception {
    released(
        "create table act (celex text not null);",
        "create table draft (id int not null);",
        "comment on table draft is 'deprecated: nothing reads it';");
    pending("NEXT_001__draft_dropped.sql", "drop table draft;");

    assertThat(check().problems()).isEmpty();
  }

  @Test
  void failsANewRequiredColumn() throws Exception {
    released("create table act (celex text not null);");
    pending("NEXT_001__act_title.sql", "alter table act add column title text not null;");

    assertThat(check().problems())
        .singleElement()
        .asString()
        .contains("act.title is new and not null without a default");
  }

  @Test
  void passesANewTableWithRequiredColumns() throws Exception {
    released("create table act (celex text not null);");
    pending("NEXT_001__draft.sql", "create table draft (id int not null);");

    assertThat(check().problems()).isEmpty();
  }

  @Test
  void failsAColumnThatBecomesRequired() throws Exception {
    released("create table act (celex text not null, title text);");
    pending("NEXT_001__act_title.sql", "alter table act alter column title set not null;");

    assertThat(check().problems())
        .singleElement()
        .asString()
        .contains("act.title becomes not null without a default");
  }

  @Test
  void failsABreakingMarkOnceAReleaseAfterTheLastBreakingOneIsOut() throws Exception {
    released("create table act (celex text not null, title text);");
    write("V2026_11_5_001__act_date.sql", "alter table act add column dated date;");
    pending(
        "NEXT_001__act_title.sql",
        "-- breaking: titles move to act_title",
        "alter table act drop column title;");

    var report = check();

    assertThat(report.passes()).isFalse();
    assertThat(report.breaking()).isEmpty();
    assertThat(report.problems())
        .anySatisfy(problem -> assertThat(problem).contains("column act.title"))
        .anySatisfy(
            problem -> assertThat(problem).contains("only the release after v2026.11.4 may"));
  }

  @Test
  void passesTheLastBreakingReleaseWhenItSaysWhy() throws Exception {
    released("create table act (celex text not null, title text);");
    write("V2026_11_4_001__act_date.sql", "alter table act add column dated date;");
    pending(
        "NEXT_001__act_title.sql",
        "-- breaking: titles move to act_title, the database is ingested again",
        "alter table act drop column title;");

    var report = check();

    assertThat(report.passes()).isTrue();
    assertThat(report.problems()).singleElement().asString().contains("column act.title");
    assertThat(report.breaking())
        .containsExactly(
            "act/NEXT_001__act_title.sql: titles move to act_title, the database is ingested"
                + " again");
  }

  private ExpandContract.Report check() throws IOException, SQLException {
    var container = java.util.Objects.requireNonNull(postgres);
    var name = "test_" + DATABASES.incrementAndGet();
    try (var connection = Postgres.dataSource(container).getConnection();
        var statement = connection.createStatement()) {
      // name is test_ and a counter: no input
      // nosemgrep: semgrep.jdbc-no-raw-sql-concatenation
      statement.execute("create database " + name);
    }
    return ExpandContract.check(Postgres.dataSource(container, name), migrations);
  }

  private void released(String... statements) throws IOException {
    write("V2026_11_0_001__act.sql", statements);
  }

  private void pending(String file, String... statements) throws IOException {
    write(file, statements);
  }

  private void write(String file, String... statements) throws IOException {
    var module = Files.createDirectories(migrations.resolve("act"));
    Files.writeString(module.resolve(file), String.join("\n", statements) + "\n");
  }
}
