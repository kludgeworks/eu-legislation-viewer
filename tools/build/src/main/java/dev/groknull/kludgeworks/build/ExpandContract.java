// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.flyway.ModuleMigrations;
import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;

/**
 * Checks that the release being made keeps the schema the running release uses: expand, then
 * contract, a release apart. During a rolling deployment the previous release's pods run on the new
 * schema, so a pending migration must not take away or change what that release reads and writes.
 *
 * <p>It compares two schemas on one database: the released migrations' (the V files, the schema of
 * the release that runs), and that schema with the pending migrations (NEXT_) run on it. A table or
 * column that is gone, or whose type changed, must have been deprecated in the released schema: its
 * comment starts with {@code deprecated:}. jOOQ's code generation leaves deprecated objects out
 * ({@code JooqCodegen}), so a release that deprecates something stops using it, and the next
 * release may drop it. Renames are caught as the old name gone, whatever SQL made them.
 *
 * <p>It also checks the writes of the previous release: a column it does not know of, or a
 * deprecated one the code no longer writes, must be nullable or have a default; and a column must
 * not become required.
 *
 * <p>Every release deploys without downtime. One release is let off: the one after v2026.11.4,
 * written before this check, which replaces the text tables and renames columns, and whose database
 * is ingested again. Its pending migrations say so on a line of their own: {@code -- breaking:
 * <reason>}. Once a release after v2026.11.4 is out (a V file names it), a mark fails the check: no
 * release breaks the running one again.
 */
public final class ExpandContract {
  private static final String DEPRECATED = "deprecated:";
  private static final Pattern BREAKING =
      Pattern.compile("^--\\s*breaking:\\s*(.+)$", Pattern.MULTILINE);
  private static final Pattern PENDING = Pattern.compile("NEXT_\\d{3}__.+\\.sql");
  private static final Pattern RELEASED = Pattern.compile("V(\\d+)_(\\d+)_(\\d+)_\\d{3}__.+\\.sql");

  /** The release the last breaking one follows: none after it may break the running release. */
  private static final List<Integer> LAST_BREAK_FOLLOWS = List.of(2026, 11, 4);

  private static final String COLUMNS =
      """
      select c.relname, a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull,
        a.atthasdef or a.attidentity <> '' or a.attgenerated <> '',
        obj_description(c.oid, 'pg_class'), col_description(c.oid, a.attnum)
      from pg_class as c
      join pg_namespace as n on n.oid = c.relnamespace
      join pg_attribute as a on a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped
      where n.nspname = 'public' and c.relkind in ('r', 'p')
        and c.relname not like 'flyway\\_schema\\_history%'
      order by c.relname, a.attnum
      """;

  private ExpandContract() {}

  /** What the check found: its problems, and the reasons a pending migration gives for breaking. */
  public record Report(List<String> problems, List<String> breaking) {
    /** Whether the release may be deployed while the previous one runs. */
    public boolean passes() {
      return problems.isEmpty() || !breaking.isEmpty();
    }
  }

  record Column(String type, boolean required, @Nullable String comment) {}

  record Table(@Nullable String comment, Map<String, Column> columns) {}

  /**
   * Migrates an empty database with the modules' migrations below {@code migrations}, and checks.
   */
  public static Report check(DataSource database, Path migrations)
      throws IOException, SQLException {
    var modules = ModuleMigrations.modules(migrations);
    for (var module : modules) {
      // Flyway's own resolvers: only the released V files, the pending NEXT_ ones are no migration
      ModuleMigrations.configure(Flyway.configure(), module).dataSource(database).load().migrate();
    }
    var released = schema(database);
    for (var module : modules) {
      ModuleMigrations.configure(PendingMigrations.configure(Flyway.configure()), module)
          .dataSource(database)
          .load()
          .migrate();
    }
    var problems = new ArrayList<>(problems(released, schema(database)));
    var breaking = breaking(modules);
    if (!breaking.isEmpty() && !mayBreak(modules)) {
      problems.add(
          "a pending migration breaks the running release (-- breaking:), but only the release"
              + " after v2026.11.4 may: expand, then contract, a release apart");
      return new Report(problems, List.of());
    }
    return new Report(problems, breaking);
  }

  /** Whether no release after the last breaking one is out yet: no V file names one. */
  static boolean mayBreak(List<Path> modules) throws IOException {
    for (var module : modules) {
      try (Stream<Path> files = Files.list(module)) {
        for (var file : files.toList()) {
          var released = RELEASED.matcher(file.getFileName().toString());
          if (released.matches()) {
            var version =
                List.of(
                    Integer.parseInt(released.group(1)),
                    Integer.parseInt(released.group(2)),
                    Integer.parseInt(released.group(3)));
            if (compare(version, LAST_BREAK_FOLLOWS) > 0) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private static int compare(List<Integer> a, List<Integer> b) {
    for (int i = 0; i < a.size(); i++) {
      var order = Integer.compare(a.get(i), b.get(i));
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  static List<String> problems(Map<String, Table> released, Map<String, Table> next) {
    var problems = new ArrayList<String>();
    released.forEach(
        (name, was) -> {
          var now = next.get(name);
          if (now == null) {
            if (!deprecated(was.comment())) {
              problems.add(
                  "table "
                      + name
                      + " is dropped or renamed, but the released schema does not deprecate it");
            }
            return;
          }
          was.columns()
              .forEach(
                  (column, before) -> {
                    var after = now.columns().get(column);
                    var qualified = name + "." + column;
                    var gone = deprecated(before.comment()) || deprecated(was.comment());
                    if (after == null) {
                      if (!gone) {
                        problems.add(
                            "column "
                                + qualified
                                + " is dropped or renamed, but the released schema does not"
                                + " deprecate it");
                      }
                    } else if (!after.type().equals(before.type())) {
                      if (!gone) {
                        problems.add(
                            "column "
                                + qualified
                                + " changes type from "
                                + before.type()
                                + " to "
                                + after.type()
                                + ", which the released code still reads");
                      }
                    } else if (after.required() && !before.required()) {
                      problems.add(
                          "column "
                              + qualified
                              + " becomes not null without a default, which the released code"
                              + " may not write");
                    }
                  });
          now.columns()
              .forEach(
                  (column, after) -> {
                    if (!was.columns().containsKey(column) && after.required()) {
                      problems.add(
                          "column "
                              + name
                              + "."
                              + column
                              + " is new and not null without a default, which the released code"
                              + " does not write");
                    }
                  });
        });
    next.forEach(
        (name, table) ->
            table
                .columns()
                .forEach(
                    (column, after) -> {
                      if (deprecated(after.comment()) && after.required()) {
                        problems.add(
                            "column "
                                + name
                                + "."
                                + column
                                + " is deprecated but not null without a default, which the code"
                                + " no longer writes");
                      }
                    }));
    return problems;
  }

  private static boolean deprecated(@Nullable String comment) {
    return comment != null && comment.startsWith(DEPRECATED);
  }

  /** The schema's tables and their columns, by name. */
  static Map<String, Table> schema(DataSource database) throws SQLException {
    var tables = new LinkedHashMap<String, Table>();
    try (var connection = database.getConnection();
        var statement = connection.prepareStatement(COLUMNS);
        var rows = statement.executeQuery()) {
      while (rows.next()) {
        var comment = rows.getString(6);
        tables
            .computeIfAbsent(rows.getString(1), name -> new Table(comment, new LinkedHashMap<>()))
            .columns()
            .put(
                rows.getString(2),
                new Column(
                    rows.getString(3),
                    rows.getBoolean(4) && !rows.getBoolean(5),
                    rows.getString(7)));
      }
    }
    return tables;
  }

  /** The reasons the pending migrations give for breaking the running release, if any. */
  static List<String> breaking(List<Path> modules) throws IOException {
    var reasons = new ArrayList<String>();
    for (var module : modules) {
      try (Stream<Path> files = Files.list(module)) {
        for (var file :
            files
                .filter(f -> PENDING.matcher(f.getFileName().toString()).matches())
                .sorted()
                .toList()) {
          var matcher = BREAKING.matcher(Files.readString(file));
          while (matcher.find()) {
            reasons.add(module.getFileName() + "/" + file.getFileName() + ": " + matcher.group(1));
          }
        }
      }
    }
    return reasons;
  }

  /**
   * Checks the modules' migrations in the directory given, on embedded Postgres, and fails if the
   * release would break the running one without saying so.
   */
  public static void main(String[] args) throws IOException, SQLException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: ExpandContract <migrations>");
    }
    Report report;
    try (var postgres = EmbeddedPostgres.start()) {
      report = check(postgres.getPostgresDatabase(), Path.of(args[0]));
    }
    report.problems().forEach(problem -> System.out.println("✗ " + problem));
    if (report.problems().isEmpty()) {
      System.out.println("✔ The pending migrations keep the schema the released code uses");
    } else if (!report.breaking().isEmpty()) {
      System.out.println(
          "The release after v2026.11.4 breaks the running one on purpose: deploy it with"
              + " downtime");
      report.breaking().forEach(reason -> System.out.println("  " + reason));
    } else {
      System.out.println(
          """
          Expand, then contract, a release apart: deprecate it (comment on … is 'deprecated: …')
          in this release and drop it in the next\
          """);
      System.exit(1);
    }
  }
}
