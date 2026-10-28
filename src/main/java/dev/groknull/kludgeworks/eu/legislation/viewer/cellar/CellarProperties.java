// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How the viewer talks to Cellar ({@code viewer.cellar.*}).
 *
 * @param sparqlEndpoint the SPARQL endpoint
 * @param resources where Cellar's resource URIs start ({@code …/resource/celex/32015R1998})
 * @param userAgent sent with every request: who we are and where to reach us
 * @param maxConcurrentRequests requests in flight at once
 * @param minInterval the least time between the starts of two requests
 * @param connectTimeout to open a connection
 * @param readTimeout for a response: large SPARQL results take minutes
 * @param retries further attempts after a network error, a 5xx or a 429
 * @param retryDelay before the first retry; doubled for each next one
 * @param maxRows a SPARQL result with this many rows may have been cut off by the endpoint
 */
@ConfigurationProperties("viewer.cellar")
public record CellarProperties(
    @DefaultValue("https://publications.europa.eu/webapi/rdf/sparql") URI sparqlEndpoint,
    @DefaultValue("http://publications.europa.eu/resource/") URI resources,
    @DefaultValue("kludgeworks-viewer/0.0 (+https://kludgeworks.groknull.dev)") String userAgent,
    @DefaultValue("1") int maxConcurrentRequests,
    @DefaultValue("200ms") Duration minInterval,
    @DefaultValue("10s") Duration connectTimeout,
    @DefaultValue("5m") Duration readTimeout,
    @DefaultValue("3") int retries,
    @DefaultValue("1s") Duration retryDelay,
    @DefaultValue("1000000") int maxRows) {

  /** The defaults, for tests and tools that run without Spring Boot's binding. */
  public static CellarProperties defaults() {
    return new CellarProperties(
        URI.create("https://publications.europa.eu/webapi/rdf/sparql"),
        URI.create("http://publications.europa.eu/resource/"),
        "kludgeworks-viewer/0.0 (+https://kludgeworks.groknull.dev)",
        1,
        Duration.ofMillis(200),
        Duration.ofSeconds(10),
        Duration.ofMinutes(5),
        3,
        Duration.ofSeconds(1),
        1_000_000);
  }
}
