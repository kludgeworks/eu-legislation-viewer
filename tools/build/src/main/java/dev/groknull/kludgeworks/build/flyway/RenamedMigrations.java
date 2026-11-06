// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.flyway;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.util.Arrays;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * A pending migration's new name, in a database it already ran on: before Flyway validates, the
 * schema history's row of a pending migration (version 9999.NNN: {@link PendingMigrations}) whose
 * file is gone is moved to the migration that is that file renamed: the one not run yet with the
 * same description and contents (checksum). So a database the app ran on in development stays valid
 * when the release gives its pending migrations their final names, or the lint numbers them again
 * (release:db-migrations --fix); a pending migration changed since it ran still fails, as Flyway
 * would have it.
 */
public final class RenamedMigrations implements Callback {
  private static final Log LOG = LogFactory.getLog(RenamedMigrations.class);

  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_VALIDATE;
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return true;
  }

  @Override
  public void handle(Event event, Context context) {
    var configuration = context.getConfiguration();
    var migrations = Arrays.asList(history(configuration));
    var notRun =
        migrations.stream().filter(m -> m.getState().isResolved() && !m.isApplied()).toList();
    for (var gone : migrations) {
      if (!isPendingWithoutFile(gone)) {
        continue;
      }
      var renamed =
          notRun.stream()
              .filter(m -> m.getDescription().equals(gone.getDescription()))
              .filter(m -> Objects.equals(m.getChecksum(), gone.getChecksum()))
              .toList();
      if (renamed.size() == 1) {
        move(context, configuration, gone, renamed.getFirst());
      }
    }
  }

  @Override
  public String getCallbackName() {
    return getClass().getSimpleName();
  }

  /** The migrations, as Flyway sees them, without the callbacks: not this one again. */
  private static MigrationInfo[] history(Configuration configuration) {
    return Flyway.configure(configuration.getClassLoader())
        .configuration(configuration)
        .callbacks(new Callback[0])
        .load()
        .info()
        .all();
  }

  private static boolean isPendingWithoutFile(MigrationInfo migration) {
    return migration.isApplied()
        && !migration.getState().isResolved()
        && !migration.getState().isFailed()
        && migration.getVersion() != null
        && migration.getVersion().getVersion().startsWith(PendingMigrations.VERSION + ".");
  }

  private static void move(
      Context context, Configuration configuration, MigrationInfo from, MigrationInfo to) {
    var schema = configuration.getDefaultSchema();
    var table = configuration.getTable();
    DSL.using(context.getConnection(), SQLDialect.POSTGRES)
        .update(table(schema == null ? name(table) : name(schema, table)))
        .set(field(name("version")), to.getVersion().getVersion())
        .set(field(name("script")), to.getScript())
        .where(field(name("installed_rank")).eq(from.getInstalledRank()))
        .execute();
    LOG.info(
        "Moved %s in %s to %s: its file was renamed"
            .formatted(from.getScript(), table, to.getScript()));
  }
}
