// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.flyway;

import java.io.Reader;
import java.util.Collection;
import java.util.regex.Pattern;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resolver.sql.SqlMigrationResolver;
import org.jspecify.annotations.Nullable;

/**
 * Migrations not released yet, for Flyway: a module's NEXT_001__description.sql runs as version
 * 9999.001, after every released migration (V2026_11_0_001__description.sql), by Flyway's own SQL
 * migrations, as if it were named V9999_001__description.sql. The release gives it its final name
 * (tools/tasks/release.bash), so the app's jar never needs this: only what runs migrations that are
 * not released (the app in development and its tests, jOOQ's code generation) does.
 *
 * <p>It replaces Flyway's resolvers ({@link #configure}), so that a file is found once, under the
 * name it runs as. Flyway's check of migration names knows no NEXT_, so that is off: the names are
 * the lint's (release:db-migrations).
 */
public final class PendingMigrations implements MigrationResolver {
  /** The version a pending migration runs as: after any release's, 9999 being no year of ours. */
  public static final String VERSION = "9999";

  private static final Pattern PENDING = Pattern.compile("NEXT_(\\d{3}__.+)");

  /** Flyway configured for pending migrations: this resolver in place of its own. */
  public static FluentConfiguration configure(FluentConfiguration flyway) {
    return flyway
        .resolvers(new PendingMigrations())
        .skipDefaultResolvers(true)
        .validateMigrationNaming(false);
  }

  @Override
  public Collection<ResolvedMigration> resolveMigrations(Context context) {
    return new SqlMigrationResolver(
            new Renamed(context.resourceProvider),
            context.sqlScriptExecutorFactory,
            context.sqlScriptFactory,
            context.configuration,
            new ParsingContext())
        .resolveMigrations(context);
  }

  /** The name a migration runs as: a pending one's V9999_ name, any other its own. */
  public static String runsAs(String filename) {
    var pending = PENDING.matcher(filename);
    return pending.matches() ? "V" + VERSION + "_" + pending.group(1) : filename;
  }

  /** The module's files, each under the name it runs as. */
  private record Renamed(ResourceProvider files) implements ResourceProvider {
    @Override
    public @Nullable LoadableResource getResource(String name) {
      return files.getResource(name);
    }

    @Override
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
      return files.getResources("", suffixes).stream()
          .map(file -> runsAs(file.getFilename()).equals(file.getFilename()) ? file : as(file))
          .filter(file -> file.getFilename().startsWith(prefix))
          .toList();
    }

    private static LoadableResource as(LoadableResource file) {
      return new LoadableResource() {
        @Override
        public Reader read() {
          return file.read();
        }

        @Override
        public boolean shouldStream() {
          return file.shouldStream();
        }

        @Override
        public String getAbsolutePath() {
          return file.getAbsolutePath();
        }

        @Override
        public String getAbsolutePathOnDisk() {
          return file.getAbsolutePathOnDisk();
        }

        @Override
        public String getFilename() {
          return runsAs(file.getFilename());
        }

        @Override
        public String getRelativePath() {
          var path = file.getRelativePath();
          return path.substring(0, path.length() - file.getFilename().length()) + getFilename();
        }
      };
    }
  }
}
