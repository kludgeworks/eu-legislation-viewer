// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.flyway.ModuleMigrations;
import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.flywaydb.core.Flyway;

/**
 * Runs a command on a database with the app's schema, for tools that read a schema from a live
 * database, such as tbls ({@code mise run db:diagram}).
 *
 * <p>The database is Postgres itself, embedded (zonky's binaries, the major version production
 * runs, no Docker), as for jOOQ's code generation ({@code JooqCodegen}). The modules' migrations
 * run as Spring Modulith runs them ({@link ModuleMigrations}), and the migrations not released yet
 * run too, after the released ones ({@link PendingMigrations}). The command gets the database's
 * address in {@code DATABASE_URL}, and Postgres stops when it ends.
 *
 * <p>Arguments: the directory of the modules' migrations, then the command. Relative paths and the
 * command are taken from the workspace when run by {@code bazel run}.
 */
public final class SchemaDatabase {
  private SchemaDatabase() {}

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: SchemaDatabase <migrations> <command…>");
    }
    var workspace = Path.of(System.getenv().getOrDefault("BUILD_WORKSPACE_DIRECTORY", "."));
    var migrations = workspace.resolve(args[0]);
    var command = Arrays.asList(args).subList(1, args.length);
    try (var postgres = EmbeddedPostgres.start()) {
      for (var module : ModuleMigrations.modules(migrations)) {
        ModuleMigrations.configure(PendingMigrations.configure(Flyway.configure()), module)
            .dataSource(postgres.getPostgresDatabase())
            .load()
            .migrate();
      }
      var process = new ProcessBuilder(command).directory(workspace.toFile()).inheritIO();
      process
          .environment()
          .put(
              "DATABASE_URL",
              "postgres://postgres@localhost:" + postgres.getPort() + "/postgres?sslmode=disable");
      var exit = process.start().waitFor();
      if (exit != 0) {
        System.exit(exit);
      }
    }
  }
}
