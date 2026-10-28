// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cellar's responses as recorded (src/test/resources/cellar), and a mock Cellar to serve them. */
final class Recorded {
  static final URI SPARQL = URI.create("https://publications.europa.eu/webapi/rdf/sparql");

  private Recorded() {}

  static byte[] bytes(String name) {
    try (var in =
        Objects.requireNonNull(Recorded.class.getResourceAsStream("/cellar/" + name), name)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Cellar's defaults without the waiting: no spacing, retries after 1 ms. */
  static CellarProperties properties(int maxRows) {
    var defaults = CellarProperties.defaults();
    return new CellarProperties(
        defaults.sparqlEndpoint(),
        defaults.resources(),
        defaults.userAgent(),
        4,
        Duration.ZERO,
        defaults.connectTimeout(),
        defaults.readTimeout(),
        2,
        Duration.ofMillis(1),
        maxRows);
  }

  /** A client of a mock Cellar: what it answers is set up on {@link #server}. */
  record Mock(MockRestServiceServer server, CellarHttp http) {
    static Mock of(CellarProperties properties) {
      var builder = RestClient.builder();
      var server = MockRestServiceServer.bindTo(builder).build();
      return new Mock(server, new CellarHttp(builder.build(), properties));
    }

    static Mock of() {
      return of(properties(1_000_000));
    }
  }
}
