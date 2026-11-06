// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The app as one file starts: the Spring Boot jar (//viewer:jar) that SpringBootJar puts together,
 * its own layout, names and loader, which the tests on Bazel's classpath never see. As deployed
 * (profile prod), on its store, Postgres in a container, which it migrates as it starts: every
 * migration applied. That the started app works (its web server, Actuator) is
 * ViewerApplicationTests'.
 *
 * <p>Until the release names them, the jar's pending migrations (NEXT_001__description.sql) are
 * names its Flyway refuses; so while there are any, it migrates a copy of the migrations instead,
 * each named as development runs it (PendingMigrations: V9999_001__description.sql), on the
 * classpath ahead of the jar, its own pending ones passed over. On the classpath, as Spring
 * Modulith looks for a module's migrations only there (not in a filesystem: location, which it
 * would skip without a word). A release's jar has none, and migrates its own: java -jar.
 */
@AutoBazelRepository
class ViewerJarTests {

  // how long the jar may take to start: generous, as the other tests start beside it
  private static final Duration STARTUP = Duration.ofMinutes(2);

  // the stand-ins' root, on the classpath: db/migration/{module}/V9999_001__description.sql
  @TempDir Path standIns;

  @Test
  void startsAsOneJar() throws Exception {
    var runfiles = Runfiles.preload().withSourceRepository(AutoBazelRepository_ViewerJarTests.NAME);
    var jar =
        runfiles.rlocation(
            "kludgeworks_viewer/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar");
    var migrations =
        Path.of(runfiles.rlocation("kludgeworks_viewer/viewer/src/main/resources/db/migration"));
    var java = ProcessHandle.current().info().command().orElseThrow();
    try (var postgres = TestcontainersConfiguration.postgres();
        var valkey = TestcontainersConfiguration.valkey()) {
      postgres.start();
      valkey.start();
      var command = new ArrayList<>(List.of(java));
      if (standInsFor(migrations)) {
        command.addAll(
            List.of(
                "-cp",
                standIns + File.pathSeparator + jar,
                "org.springframework.boot.loader.launch.JarLauncher",
                "--spring.flyway.validate-migration-naming=false"));
      } else {
        command.addAll(List.of("-jar", jar));
      }
      command.addAll(
          List.of(
              "--server.port=0",
              "--spring.profiles.active=prod",
              "--spring.datasource.url=" + postgres.getJdbcUrl(),
              "--spring.datasource.username=" + postgres.getUsername(),
              "--spring.datasource.password=" + postgres.getPassword(),
              "--spring.data.redis.host=" + valkey.getHost(),
              "--spring.data.redis.port="
                  + valkey.getMappedPort(TestcontainersConfiguration.VALKEY_PORT)));
      var app = new ProcessBuilder(command).redirectErrorStream(true).start();
      assertStarts(app);
      assertThat(applied(postgres))
          .as("the migrations applied, in every module's history")
          .isEqualTo(count(migrations));
    }
  }

  /** The migrations applied, in every module's history ({@code flyway_schema_history_<module>}). */
  private static int applied(PostgreSQLContainer postgres) throws Exception {
    try (var connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      var db = DSL.using(connection, SQLDialect.POSTGRES);
      var histories =
          db.select(field(name("table_name"), String.class))
              .from(table(name("information_schema", "tables")))
              .where(field(name("table_name"), String.class).like("flyway_schema_history%"))
              .fetch(0, String.class);
      var applied = 0;
      for (var history : histories) {
        // a migration file's row (not the BASELINE row a module's history starts with)
        applied +=
            db.fetchCount(
                table(name(history)),
                field(name("type")).eq("SQL").and(field(name("success")).isTrue()));
      }
      return applied;
    }
  }

  private static long count(Path migrations) throws IOException {
    try (var found = Files.walk(migrations)) {
      return found.filter(Files::isRegularFile).count();
    }
  }

  /**
   * Whether there are pending migrations: if so, every migration copied under standIns, in its
   * module's directory, under the name it runs as.
   */
  private boolean standInsFor(Path migrations) throws IOException {
    List<Path> files;
    try (var found = Files.walk(migrations)) {
      files = found.filter(Files::isRegularFile).toList();
    }
    var pending =
        files.stream()
            .anyMatch(
                file -> {
                  var name = file.getFileName().toString();
                  return !PendingMigrations.runsAs(name).equals(name);
                });
    if (pending) {
      for (var file : files) {
        var module =
            standIns
                .resolve("db/migration")
                .resolve(migrations.relativize(file.getParent()).toString());
        Files.createDirectories(module);
        Files.copy(file, module.resolve(PendingMigrations.runsAs(file.getFileName().toString())));
      }
    }
    return pending;
  }

  private static void assertStarts(Process app) throws Exception {
    try {
      var started =
          CompletableFuture.supplyAsync(
              () ->
                  app.inputReader()
                      .lines()
                      .anyMatch(line -> line.contains("Started ViewerApplication")));

      assertThat(started.get(STARTUP.toSeconds(), TimeUnit.SECONDS))
          .as("the app's log says it started")
          .isTrue();
    } finally {
      app.destroy();
      app.waitFor();
    }
  }
}
