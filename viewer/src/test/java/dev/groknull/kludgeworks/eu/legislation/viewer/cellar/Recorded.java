// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Gives Cellar's recorded responses and a mock Cellar to serve them. The responses are in {@code
 * viewer/src/test/resources/cellar}.
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

  /** The Cellar settings from {@code application.yaml}, with the row limit {@code maxRows}. */
  static CellarProperties properties(int maxRows) {
    return new CellarProperties(
        SPARQL,
        URI.create("http://publications.europa.eu/resource/"),
        "kludgeworks-viewer/0.0 (+https://kludgeworks.groknull.dev)",
        maxRows);
  }

  /** A client of a mock Cellar. A test sets up the answers on {@link #server}. */
  record Mock(MockRestServiceServer server, RestClient client, CellarProperties properties) {
    static Mock of(CellarProperties properties) {
      var builder = RestClient.builder();
      var server = MockRestServiceServer.bindTo(builder).build();
      return new Mock(server, builder.build(), properties);
    }

    /** Runs each query on its own virtual thread, as Spring Boot's task executor does. */
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
