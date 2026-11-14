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
 * Tests that the whole application starts on a web server, that Actuator says it is up, and that
 * the API answers as the web server is configured for it. The store is Postgres in a container, and
 * the test migrates it.
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

  @Test
  void answersTheRootOfTheApiInHal() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api")).build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type")).contains("application/hal+json");
      assertThat(response.body()).contains("\"av:act\"");
    }
  }

  /**
   * Tests that Tomcat passes an encoded slash on to Spring MVC, as the address of an act with a
   * slash in its CELEX number needs. By default, Tomcat refuses it with 400. The address asked
   * needs nothing of Cellar: it is a resource of an act that does not exist.
   */
  @Test
  void takesAnEncodedSlashInAPathSegment() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://localhost:" + port + "/api/acts/a%2Fb/unknown"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(404);
    }
  }
}
