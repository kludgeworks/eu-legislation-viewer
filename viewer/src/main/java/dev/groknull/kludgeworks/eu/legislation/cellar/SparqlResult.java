// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.cellar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * A SPARQL SELECT result. It has the variables, and one row per solution. A row holds the value of
 * each bound variable. An unbound variable has no entry.
 */
public record SparqlResult(List<String> variables, List<Row> rows) {

  public SparqlResult {
    variables = List.copyOf(variables);
    rows = List.copyOf(rows);
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /**
   * Parses SPARQL 1.1 Query Results JSON. The endpoint answers in this format, and the tests
   * recorded its answers in it. The format is {@code head.vars} and {@code results.bindings[{var:
   * {type, value}}]}.
   */
  public static SparqlResult parse(byte[] json) {
    var results = JSON.readTree(json);
    var variables = new ArrayList<String>();
    results.path("head").path("vars").forEach(v -> variables.add(v.asString()));
    var rows = new ArrayList<Row>();
    for (var binding : results.path("results").path("bindings")) {
      var values = new LinkedHashMap<String, String>();
      for (var variable : variables) {
        var value = binding.path(variable).path("value");
        if (!value.isMissingNode()) {
          values.put(variable, value.asString());
        }
      }
      rows.add(new Row(values));
    }
    return new SparqlResult(variables, rows);
  }

  /** One solution. */
  public record Row(Map<String, String> values) {
    public Row {
      values = Map.copyOf(values);
    }

    /** The value of a variable the query always binds. */
    public String get(String variable) {
      var value = values.get(variable);
      if (value == null) {
        throw new CellarException("SPARQL result has no value for ?" + variable);
      }
      return value;
    }

    /** The value of a variable the query may leave unbound. */
    public Optional<String> find(String variable) {
      return Optional.ofNullable(values.get(variable));
    }
  }
}
