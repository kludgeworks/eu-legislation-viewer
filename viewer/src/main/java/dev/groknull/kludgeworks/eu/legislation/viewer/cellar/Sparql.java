// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SELECT queries against Cellar's SPARQL endpoint. The same query asked again while it runs waits
 * for that answer instead of asking Cellar twice.
 */
@Component
public class Sparql {
  private static final MediaType RESULTS =
      MediaType.parseMediaType("application/sparql-results+json");

  private final RestClient client;
  private final CellarProperties properties;
  private final ConcurrentHashMap<String, CompletableFuture<SparqlResult>> running =
      new ConcurrentHashMap<>();

  Sparql(@Qualifier(CellarConfiguration.CLIENT) RestClient client, CellarProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /**
   * Runs a SELECT query.
   *
   * @throws CellarException if the result has as many rows as the endpoint returns at most ({@code
   *     viewer.cellar.max-rows}) and so may be cut off
   */
  public SparqlResult select(String query) {
    var mine = new CompletableFuture<SparqlResult>();
    var theirs = running.putIfAbsent(query, mine);
    if (theirs != null) {
      return join(theirs);
    }
    try {
      var result = ask(query);
      mine.complete(result);
      return result;
    } catch (RuntimeException e) {
      mine.completeExceptionally(e);
      throw e;
    } finally {
      running.remove(query, mine);
    }
  }

  /**
   * A query's answer once it has one, from a future of {@link #select}'s: a failure as it was
   * thrown, not wrapped in the future's CompletionException, so it reaches CellarProblems as
   * itself.
   */
  public static SparqlResult join(CompletableFuture<SparqlResult> answer) {
    try {
      return answer.join();
    } catch (CompletionException e) {
      throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
  }

  private SparqlResult ask(String query) {
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
