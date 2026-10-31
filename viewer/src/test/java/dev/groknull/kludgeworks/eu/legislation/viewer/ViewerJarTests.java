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
 * That the started app works (its web server, Valkey, Actuator) is ViewerApplicationTests'; Valkey
 * isn't needed to start, as its client connects on first use.
 */
@AutoBazelRepository
class ViewerJarTests {

  private static final Duration STARTUP = Duration.ofSeconds(30);

  @Test
  void startsAsOneJar() throws Exception {
    var jar =
        Runfiles.preload()
            .withSourceRepository(AutoBazelRepository_ViewerJarTests.NAME)
            .rlocation(
                "kludgeworks_viewer/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar");
    var java = ProcessHandle.current().info().command().orElseThrow();
    var app =
        new ProcessBuilder(java, "-jar", jar, "--server.port=0").redirectErrorStream(true).start();
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
