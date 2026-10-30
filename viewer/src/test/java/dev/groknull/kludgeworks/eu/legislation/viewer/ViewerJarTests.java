// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The app as one file starts with all of it: java -jar on the Spring Boot jar (//viewer:jar) brings
 * up the web server and Actuator, as on Bazel's classpath (ViewerApplicationTests).
 */
@AutoBazelRepository
class ViewerJarTests {

  private static final Duration STARTUP = Duration.ofSeconds(30);

  @Test
  void runsAsOneJarWithItsWebServer() throws Exception {
    var jar =
        Path.of(
            Runfiles.preload()
                .withSourceRepository(AutoBazelRepository_ViewerJarTests.NAME)
                .rlocation(
                    "kludgeworks_viewer/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar"));
    var port = freePort();
    var java = ProcessHandle.current().info().command().orElseThrow();
    var app =
        new ProcessBuilder(java, "-jar", jar.toString(), "--server.port=" + port)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    try (var client = HttpClient.newHttpClient()) {
      var response = health(client, port);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    } finally {
      app.destroy();
      app.waitFor();
    }
  }

  /** The health endpoint's answer, once the app listens. */
  private static HttpResponse<String> health(HttpClient client, int port) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build();
    var deadline = Instant.now().plus(STARTUP);
    while (true) {
      try {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (ConnectException e) {
        if (Instant.now().isAfter(deadline)) {
          throw e;
        }
        Thread.sleep(200);
      }
    }
  }

  private static int freePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
