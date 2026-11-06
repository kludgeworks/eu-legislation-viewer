// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Cellar's responses as recorded (viewer/src/test/resources/cellar), and a mock Cellar to serve
 * them.
 */
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

  /** Cellar as application.yaml has it, with a given row limit. */
  static CellarProperties properties(int maxRows) {
    return new CellarProperties(
        SPARQL,
        URI.create("http://publications.europa.eu/resource/"),
        "kludgeworks-viewer/0.0 (+https://kludgeworks.groknull.dev)",
        maxRows);
  }

  /** A client of a mock Cellar: what it answers is set up on {@link #server}. */
  record Mock(MockRestServiceServer server, RestClient client, CellarProperties properties) {
    static Mock of(CellarProperties properties) {
      var builder = RestClient.builder();
      var server = MockRestServiceServer.bindTo(builder).build();
      return new Mock(server, builder.build(), properties);
    }

    /** Each query on a virtual thread of its own, as on Spring Boot's task executor. */
    Sparql sparql() {
      return new Sparql(client, properties, query -> Thread.ofVirtual().start(query));
    }

    ContentStreams contentStreams() {
      return new ContentStreams(client);
    }

    FormexFiles formexFiles() {
      return new FormexFiles(sparql(), client, properties);
    }

    static Mock of() {
      return of(Recorded.properties(1_000_000));
    }
  }
}
