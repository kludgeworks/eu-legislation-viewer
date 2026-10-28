// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SELECT queries against Cellar's SPARQL endpoint. The same query asked again while it runs waits
 * for that answer instead of asking Cellar twice.
 */
@Component
public class Sparql {
  private static final MediaType RESULTS =
      MediaType.parseMediaType("application/sparql-results+json");

  private final CellarHttp http;
  private final JsonMapper json = JsonMapper.builder().build();
  private final ConcurrentHashMap<String, CompletableFuture<SparqlResult>> running =
      new ConcurrentHashMap<>();

  Sparql(CellarHttp http) {
    this.http = http;
  }

  /**
   * Runs a SELECT query.
   *
   * @throws CellarException if Cellar fails, or the result has as many rows as the endpoint returns
   *     at most ({@code viewer.cellar.max-rows}) and so may be cut off
   */
  public SparqlResult select(String query) {
    var mine = new CompletableFuture<SparqlResult>();
    var theirs = running.putIfAbsent(query, mine);
    if (theirs != null) {
      try {
        return theirs.join();
      } catch (CompletionException e) {
        throw e.getCause() instanceof RuntimeException cause ? cause : e;
      }
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

  private SparqlResult ask(String query) {
    var body =
        http.call(
            () ->
                http.client()
                    .post()
                    .uri(http.properties().sparqlEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(RESULTS)
                    .body("query=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class));
    if (body == null) {
      throw new CellarException("SPARQL endpoint returned no body");
    }
    var result = parse(json.readTree(body));
    if (result.rows().size() >= http.properties().maxRows()) {
      throw new CellarException(
          "SPARQL result has "
              + result.rows().size()
              + " rows, the endpoint's limit: it may be cut off");
    }
    return result;
  }

  /** SPARQL 1.1 Query Results JSON: head.vars, results.bindings[{var: {type, value}}]. */
  static SparqlResult parse(JsonNode results) {
    var variables = new ArrayList<String>();
    results.path("head").path("vars").forEach(v -> variables.add(v.asString()));
    var rows = new ArrayList<SparqlResult.Row>();
    for (var binding : results.path("results").path("bindings")) {
      var values = new LinkedHashMap<String, String>();
      for (var variable : variables) {
        var value = binding.path(variable).path("value");
        if (!value.isMissingNode()) {
          values.put(variable, value.asString());
        }
      }
      rows.add(new SparqlResult.Row(values));
    }
    return new SparqlResult(variables, rows);
  }
}
