// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A SPARQL SELECT result: the variables, and one row per solution with the value of each bound
 * variable (an unbound variable has no entry).
 */
public record SparqlResult(List<String> variables, List<Row> rows) {

  public SparqlResult {
    variables = List.copyOf(variables);
    rows = List.copyOf(rows);
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
