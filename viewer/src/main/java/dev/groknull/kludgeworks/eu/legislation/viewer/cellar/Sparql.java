// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

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
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SELECT queries against Cellar's SPARQL endpoint, each on the executor. The same query asked again
 * while it runs gets that answer, to come, instead of asking Cellar twice.
 */
@Component
public class Sparql {
  private static final Logger LOG = LoggerFactory.getLogger(Sparql.class);
  private static final MediaType RESULTS =
      MediaType.parseMediaType("application/sparql-results+json");

  private final RestClient client;
  private final CellarProperties properties;
  private final ConcurrentHashMap<String, CompletableFuture<SparqlResult>> running =
      new ConcurrentHashMap<>();

  private final Executor executor;

  /**
   * @param executor where the queries run: Spring Boot's task executor, on virtual threads, as each
   *     mostly waits on Cellar
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
   * @throws CellarException if the result has as many rows as the endpoint returns at most ({@code
   *     viewer.cellar.max-rows}) and so may be cut off
   */
  public SparqlResult select(String query) {
    return join(answer(query));
  }

  /**
   * A SELECT query's answer, to come: the one already coming if the same query is running. Each
   * caller gets a copy of it, so that completing or cancelling theirs leaves the others' alone.
   * Fails as {@link #select} throws.
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
      // the executor refused it
      mine.completeExceptionally(e);
    }
    return mine.copy();
  }

  /**
   * A query's answer once it has one, from {@link #answer}: a failure as it was thrown, not wrapped
   * in the future's CompletionException, so its caller sees it as itself.
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
   * A query kept beside the class that runs it: {@code name} in its package on the classpath, with
   * {@code {{placeholders}}} for {@link #fill} (the corpus script's queries use the same).
   */
  public static String template(Class<?> owner, String name) {
    try (var in = Objects.requireNonNull(owner.getResourceAsStream(name), name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The query with each {@code {{name}}} replaced by its value, escaped for a string literal
   * (SPARQL 1.1, ECHAR), where the templates put them ("{{celex}}"^^xsd:string): a value is never
   * read as part of the query, whatever it holds. A value put into an IRI the caller checks.
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
   * {@code value} as the inside of a SPARQL string literal: backslashes, quotes and line breaks
   * escaped.
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
