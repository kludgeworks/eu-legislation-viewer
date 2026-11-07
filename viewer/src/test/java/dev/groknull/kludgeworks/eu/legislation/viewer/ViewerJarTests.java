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
 * Tests that the app starts as one file. The file is the Spring Boot jar {@code //viewer:jar},
 * which {@code SpringBootJar} puts together. The jar has its own layout, names and loader. The
 * tests on Bazel's classpath never see these.
 *
 * <p>The jar runs as deployed, with the profile {@code prod}. Its store is Postgres in a container.
 * The jar migrates the store when it starts, and the test checks that every migration is applied.
 * {@code ViewerApplicationTests} tests that the started app works, with its web server and
 * Actuator.
 *
 * <p>Until the release names them, the pending migrations in the jar have names that its Flyway
 * refuses, such as {@code NEXT_001__description.sql}. While there are pending migrations, the test
 * migrates a copy of the migrations instead. Each copy has the name that development runs it as,
 * from {@link PendingMigrations}. An example is {@code V9999_001__description.sql}. The copies are
 * on the classpath ahead of the jar, so the jar's own pending migrations are passed over.
 *
 * <p>The copies must be on the classpath, because Spring Modulith looks for the migrations of a
 * module only there. It skips a {@code filesystem:} location without a warning. The jar of a
 * release has no pending migrations, so the test runs it with {@code java -jar} on its own
 * migrations.
 */
@AutoBazelRepository
class ViewerJarTests {

  // How long the jar can take to start. The time is generous, because the other tests start at
  // the same time.
  private static final Duration STARTUP = Duration.ofMinutes(2);

  // The root of the stand-in migrations on the classpath. A stand-in is at
  // db/migration/{module}/V9999_001__description.sql.
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

  /**
   * The number of migrations applied, in the history of every module. A history is the table {@code
   * flyway_schema_history_<module>}.
   */
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
        // Count the rows of migration files. Do not count the BASELINE row that starts the
        // history of a module.
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
   * Whether there are pending migrations. If there are, this copies every migration under {@code
   * standIns}, into the directory of its module, with the name it runs as.
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
