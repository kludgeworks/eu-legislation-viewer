// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The app as one file starts: java -jar on the Spring Boot jar (//viewer:jar) that SpringBootJar
 * puts together, its own layout, names and loader, which the tests on Bazel's classpath never see.
 * As deployed (profile prod), on its store, Postgres in a container, which it migrates as it
 * starts. That the started app works (its web server, Actuator) is ViewerApplicationTests'.
 *
 * <p>Until the release names them, the jar's pending migrations (NEXT_001__description.sql) are
 * names its Flyway refuses; so while there are any, it migrates a copy of the migrations instead,
 * each named as development runs it (PendingMigrations: V9999_001__description.sql). A release's
 * jar has none, and migrates its own.
 */
@AutoBazelRepository
class ViewerJarTests {

  // how long the jar may take to start: generous, as the other tests start beside it
  private static final Duration STARTUP = Duration.ofMinutes(2);

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
      var command =
          new ArrayList<>(
              List.of(
                  java,
                  "-jar",
                  jar,
                  "--server.port=0",
                  "--spring.profiles.active=prod",
                  "--spring.datasource.url=" + postgres.getJdbcUrl(),
                  "--spring.datasource.username=" + postgres.getUsername(),
                  "--spring.datasource.password=" + postgres.getPassword(),
                  "--spring.data.redis.host=" + valkey.getHost(),
                  "--spring.data.redis.port="
                      + valkey.getMappedPort(TestcontainersConfiguration.VALKEY_PORT)));
      if (standInsFor(migrations)) {
        command.add("--spring.flyway.locations=filesystem:" + standIns);
      }
      var app = new ProcessBuilder(command).redirectErrorStream(true).start();
      assertStarts(app);
    }
  }

  /**
   * Whether there are pending migrations: if so, every migration copied to standIns, in its
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
        var module = standIns.resolve(migrations.relativize(file.getParent()).toString());
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
