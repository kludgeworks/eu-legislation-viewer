// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Runs SELECT queries against Cellar's SPARQL endpoint. Each query runs on the executor. If a
 * caller asks the same query while it runs, the caller gets the answer of the running query. Cellar
 * does not get the query twice.
 */
@Component
public class Sparql {
  private static final Logger LOG = LoggerFactory.getLogger(Sparql.class);
  private static final MediaType RESULTS =
      MediaType.parseMediaType("application/sparql-results+json");

  private final RestClient client;
  private final CellarProperties properties;
  private final ConcurrentMap<String, CompletableFuture<SparqlResult>> running =
      new ConcurrentHashMap<>();

  private final Executor executor;

  /**
   * @param executor where the queries run. This is Spring Boot's task executor, on virtual threads,
   *     because each query mostly waits for Cellar.
   */
  Sparql(
      @Qualifier(CellarConfiguration.CLIENT) RestClient client,
      CellarProperties properties,
      @Qualifier("applicationTaskExecutor") Executor executor) {
    this.client = client;
    this.properties = properties;
    this.executor = executor;
  }

  /**
   * Runs a SELECT query and waits for its answer.
   *
   * @throws CellarException if the result has as many rows as the endpoint returns at most, so the
   *     endpoint may have cut it off. The {@code viewer.cellar.max-rows} property sets this limit.
   */
  public SparqlResult select(String query) {
    return join(answer(query));
  }

  /**
   * Returns the future answer to a SELECT query. If the same query is running, it returns the
   * answer of that query. Each caller gets a copy of the future, so a caller that completes or
   * cancels its copy does not change the copies of other callers. The future fails in the same
   * cases where {@link #select} throws.
   */
  public CompletableFuture<SparqlResult> answer(String query) {
    var mine = new CompletableFuture<SparqlResult>();
    var theirs = running.putIfAbsent(query, mine);
    if (theirs != null) {
      LOG.debug("Waiting for the same SPARQL query, already asked");
      return theirs.copy();
    }
    mine.whenComplete((result, failure) -> running.remove(query, mine));
    try {
      mine.completeAsync(() -> ask(query), executor);
    } catch (RuntimeException e) {
      // The executor refused the task.
      mine.completeExceptionally(e);
    }
    return mine.copy();
  }

  /**
   * Waits for an answer from {@link #answer} and returns it. If the query failed, it throws the
   * failure as it was thrown, not wrapped in a {@code CompletionException}. The caller sees the
   * original failure.
   */
  public static SparqlResult join(CompletableFuture<SparqlResult> answer) {
    try {
      return answer.join();
    } catch (CompletionException e) {
      throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
  }

  private SparqlResult ask(String query) {
    LOG.trace("SPARQL query: {}", query);
    var started = System.nanoTime();
    var body =
        client
            .post()
            .uri(properties.sparqlEndpoint())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(RESULTS)
            .body("query=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
            .retrieve()
            .body(byte[].class);
    if (body == null) {
      throw new CellarException("SPARQL endpoint returned no body");
    }
    var result = SparqlResult.parse(body);
    LOG.debug(
        "SPARQL answered {} rows ({} KB) in {} ms",
        result.rows().size(),
        body.length / 1024,
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    if (result.rows().size() >= properties.maxRows()) {
      throw new CellarException(
          "SPARQL result has "
              + result.rows().size()
              + " rows, the endpoint's limit: it may be cut off");
    }
    return result;
  }

  /**
   * Reads a query that is kept next to the class that runs it. The query is the resource {@code
   * name} in the package of {@code owner} on the classpath. It has {@code {{placeholders}}} for
   * {@link #fill}. The queries of the corpus script use the same placeholders.
   */
  public static String template(Class<?> owner, String name) {
    try (var in = Objects.requireNonNull(owner.getResourceAsStream(name), name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the query with each {@code {{name}}} replaced by its value. The templates put each
   * placeholder in a string literal, for example {@code "{{celex}}"^^xsd:string}. So this method
   * escapes each value for a string literal, with the SPARQL 1.1 ECHAR rule. Whatever a value
   * holds, the endpoint never reads it as part of the query. If a template puts a value into an
   * IRI, the caller must check that value.
   */
  public static String fill(String template, Map<String, String> values) {
    var query = template;
    for (var value : values.entrySet()) {
      query = query.replace("{{" + value.getKey() + "}}", escaped(value.getValue()));
    }
    if (query.contains("{{")) {
      throw new IllegalArgumentException("A placeholder left unfilled: " + query);
    }
    return query;
  }

  /**
   * Returns {@code value} as the inside of a SPARQL string literal. It escapes backslashes, quotes
   * and line breaks.
   */
  private static String escaped(String value) {
    var escaped = new StringBuilder(value.length());
    for (var c : value.toCharArray()) {
      switch (c) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\'' -> escaped.append("\\'");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
