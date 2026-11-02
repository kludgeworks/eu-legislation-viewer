// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

/**
 * The whole application starts, on a web server, with its store (Postgres in a container) migrated,
 * and Actuator says it is up.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ViewerApplicationTests {

  @Value("${local.server.port}")
  int port;

  @Test
  void startsAndReportsItselfUp() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"status\":\"UP\"");
    }
  }
}
