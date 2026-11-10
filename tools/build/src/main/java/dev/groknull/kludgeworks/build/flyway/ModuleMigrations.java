// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * The modules' migrations as Spring Modulith runs them: each module's directory by Flyway with a
 * history table of its own, {@code __root} first, then the modules by name.
 */
public final class ModuleMigrations {
  /** The module whose migrations everything shares, Spring Modulith's root. */
  private static final String ROOT = "__root";

  private ModuleMigrations() {}

  /** The modules' directories of migrations below {@code migrations}, in the order they run. */
  public static List<Path> modules(Path migrations) throws IOException {
    try (var directories = Files.list(migrations)) {
      return directories
          .filter(Files::isDirectory)
          .sorted(
              (a, b) -> {
                var aRoot = a.getFileName().toString().equals(ROOT);
                var bRoot = b.getFileName().toString().equals(ROOT);
                return aRoot == bRoot ? a.compareTo(b) : aRoot ? -1 : 1;
              })
          .toList();
    }
  }

  /** Flyway configured for one module: its directory, and its history table. */
  public static FluentConfiguration configure(FluentConfiguration flyway, Path module) {
    return flyway
        .locations("filesystem:" + module.toAbsolutePath())
        .table("flyway_schema_history_" + module.getFileName())
        .baselineOnMigrate(true)
        .baselineVersion("0");
  }
}
