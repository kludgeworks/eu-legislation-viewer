// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.CellarChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ChangeSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads what Cellar changed from Cellar's RDF, with the query {@code changes.rq}, kept next to this
 * class. It is one query, which the scan sends once each interval.
 */
@Component
class CellarChanges implements ChangeSource {
  private static final String CHANGES = Sparql.template(CellarChanges.class, "changes.rq");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final Sparql sparql;

  CellarChanges(Sparql sparql) {
    this.sparql = sparql;
  }

  @Override
  public List<CellarChange> since(Instant since) {
    return changes(sparql.select(Sparql.fill(CHANGES, Map.of("since", since.toString()))));
  }

  /**
   * Reads the answer to {@code changes.rq}.
   *
   * @throws CellarException if a row is not a change, for example because its date is not one
   */
  static List<CellarChange> changes(SparqlResult answer) {
    var changes = new ArrayList<CellarChange>(answer.rows().size());
    for (var row : answer.rows()) {
      try {
        var targets = row.find("targets").map(String::trim).orElse("");
        changes.add(
            new CellarChange(
                row.get("celex"),
                targets.isEmpty() ? Set.of() : Set.copyOf(List.of(WHITESPACE.split(targets))),
                OffsetDateTime.parse(row.get("modified")).toInstant()));
      } catch (DateTimeException | IllegalArgumentException e) {
        throw new CellarException("Cellar's change " + row.values() + ": " + e.getMessage(), e);
      }
    }
    return changes;
  }
}
