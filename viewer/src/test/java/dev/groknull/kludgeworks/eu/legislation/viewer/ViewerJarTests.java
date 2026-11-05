// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The app as one file starts: java -jar on the Spring Boot jar (//viewer:jar) that SpringBootJar
 * puts together, its own layout, names and loader, which the tests on Bazel's classpath never see.
 * As deployed (profile prod), on its store, Postgres in a container, which it migrates as it
 * starts. That the started app works (its web server, Actuator) is ViewerApplicationTests'.
 */
@AutoBazelRepository
class ViewerJarTests {

  // how long the jar may take to start: generous, as the other tests start beside it
  private static final Duration STARTUP = Duration.ofMinutes(2);

  @Test
  void startsAsOneJar() throws Exception {
    var jar =
        Runfiles.preload()
            .withSourceRepository(AutoBazelRepository_ViewerJarTests.NAME)
            .rlocation(
                "kludgeworks_viewer/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar");
    var java = ProcessHandle.current().info().command().orElseThrow();
    try (var postgres = TestcontainersConfiguration.postgres();
        var valkey = TestcontainersConfiguration.valkey()) {
      postgres.start();
      valkey.start();
      var app =
          new ProcessBuilder(
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
                      + valkey.getMappedPort(TestcontainersConfiguration.VALKEY_PORT))
              .redirectErrorStream(true)
              .start();
      assertStarts(app);
    }
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
