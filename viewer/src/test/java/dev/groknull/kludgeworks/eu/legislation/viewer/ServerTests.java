// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The API on a real server, Tomcat, as MockMvc bypasses it: a treaty's CELEX, its slash as %2F
 * (ApiServerConfiguration), with its links. The whole app, on Valkey in a container, its acts held
 * in memory (OneTreaty).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OneTreaty.class})
class ServerTests {
  final HttpClient http = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  int port;

  @Test
  void aTreatyIsAtItsCelexItsSlashEncoded() throws IOException, InterruptedException {
    var response = get("/api/acts/12016E%2FTXT");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"celex\":\"12016E/TXT\"")
        .contains("\"href\":\"http://localhost:" + port + "/api/acts/12016E%2FTXT\"")
        .contains(
            "\"href\":\"http://localhost:" + port + "/api/acts/12016E%2FTXT/versions/2016-06-07\"");
  }

  @Test
  void aSlashTraversingOutOfItsSegmentStaysInIt() throws IOException, InterruptedException {
    // looked up as a CELEX number, which no act has: not the API's root
    assertThat(get("/api/acts/12016E%2F..%2F..%2Fapi").statusCode()).isEqualTo(404);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", "application/hal+json")
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
