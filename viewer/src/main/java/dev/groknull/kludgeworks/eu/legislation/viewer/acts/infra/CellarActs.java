// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * All acts, from Cellar's RDF (the CDM ontology): an act's work by its CELEX, found from its ELI if
 * need be, and the consolidations based on it (Research: CDM ontology). Three queries, beside this
 * class: act.rq, act-by-eli.rq and versions.rq.
 */
@Component
class CellarActs implements Acts {
  private static final String ACT = Sparql.template(CellarActs.class, "act.rq");
  private static final String ACT_BY_ELI = Sparql.template(CellarActs.class, "act-by-eli.rq");
  private static final String VERSIONS = Sparql.template(CellarActs.class, "versions.rq");

  /** Cellar's date for one it doesn't know (seen as an entry into force). */
  private static final LocalDate UNKNOWN = LocalDate.of(1001, 1, 1);

  /** Cellar's end of validity for an act that is open-ended. */
  private static final LocalDate OPEN = LocalDate.of(9999, 12, 31);

  private static final String RESOURCE_TYPE =
      "http://publications.europa.eu/resource/authority/resource-type/";

  private final Sparql sparql;

  CellarActs(Sparql sparql) {
    this.sparql = sparql;
  }

  @Override
  public Optional<Act> find(ActIdentifier id) {
    var values = Map.of("celex", id.celex());
    return act(
        id, sparql.select(Sparql.fill(ACT, values)), sparql.select(Sparql.fill(VERSIONS, values)));
  }

  @Override
  public Optional<Act> find(Eli eli) {
    return identity(eli, sparql.select(Sparql.fill(ACT_BY_ELI, Map.of("eli", eli.path()))))
        .flatMap(this::find);
  }

  /**
   * The act from the answer to act-by-eli.rq; none if no work has the ELI.
   *
   * @throws CellarException if Cellar's answer makes no act
   */
  static Optional<ActIdentifier> identity(Eli eli, SparqlResult works) {
    try {
      var celexes = new TreeSet<String>();
      for (var row : works.rows()) {
        celexes.add(row.get("celex"));
      }
      if (celexes.size() > 1) {
        throw new IllegalArgumentException("several acts have it: " + celexes);
      }
      return celexes.stream().findFirst().map(ActIdentifier::new);
    } catch (IllegalArgumentException e) {
      throw new CellarException("Cellar's data for the ELI " + eli + ": " + e.getMessage(), e);
    }
  }

  /**
   * The act from the answers to act.rq and versions.rq; none if act.rq found no work.
   *
   * @throws CellarException if Cellar's answers make no act: a value missing, repeated or not of
   *     its kind, or versions the timeline refuses
   */
  static Optional<Act> act(ActIdentifier id, SparqlResult work, SparqlResult consolidations) {
    if (work.rows().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(read(id, work, consolidations));
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new CellarException("Cellar's data for " + id + ": " + e.getMessage(), e);
    }
  }

  /** The act; an IllegalArgumentException (or a DateTimeException) for what Cellar got wrong. */
  private static Act read(ActIdentifier id, SparqlResult work, SparqlResult consolidations) {
    var row = work.rows().getFirst();
    var types = distinct(work, "type");
    if (types.size() != 1 || !types.getFirst().startsWith(RESOURCE_TYPE)) {
      throw new IllegalArgumentException("resource types " + types);
    }
    var elis = distinct(work, "eli");
    if (elis.size() > 1) {
      throw new IllegalArgumentException("several ELIs: " + elis);
    }
    var entryIntoForce =
        dates(work, "entryIntoForce").stream()
            .filter(d -> !d.equals(UNKNOWN))
            .min(Comparator.naturalOrder());
    var endOfValidity =
        dates(work, "endOfValidity").stream()
            .filter(d -> !d.equals(OPEN))
            .min(Comparator.naturalOrder());
    var dateOfDocument = row.find("dateOfDocument").map(LocalDate::parse).orElse(null);
    var originalFrom = entryIntoForce.orElse(dateOfDocument);
    if (originalFrom == null) {
      throw new IllegalArgumentException("neither an entry into force nor a date of document");
    }
    return new Act(
        id,
        elis.isEmpty() ? null : Eli.of(elis.getFirst()),
        types.getFirst().substring(RESOURCE_TYPE.length()),
        dateOfDocument,
        row.find("inForce").map(CellarActs::isTrue).orElse(false),
        endOfValidity.orElse(null),
        timeline(id, originalFrom, consolidations));
  }

  /**
   * The original text, then the consolidated versions in date order. A consolidation that starts on
   * the original's own date replaces it, as it's the later text of that day.
   */
  static Timeline timeline(ActIdentifier id, LocalDate originalFrom, SparqlResult consolidations) {
    var byVersion = new LinkedHashMap<String, List<SparqlResult.Row>>();
    for (var row : consolidations.rows()) {
      byVersion.computeIfAbsent(row.get("version"), v -> new ArrayList<>()).add(row);
    }
    var versions = new ArrayList<Version>();
    for (var rows : byVersion.values()) {
      var first = rows.getFirst();
      var integrates = new TreeSet<String>();
      for (var row : rows) {
        row.find("listed")
            .filter(ActIdentifier::isCelex)
            .filter(c -> !c.equals(id.celex()))
            .ifPresent(integrates::add);
      }
      versions.add(
          new Version(
              Version.Kind.CONSOLIDATED,
              LocalDate.parse(first.get("date")),
              first.get("version"),
              !isTrue(first.get("pdf")),
              integrates.stream().map(ActIdentifier::new).toList()));
    }
    versions.sort(Comparator.comparing(Version::date));
    if (versions.isEmpty() || versions.getFirst().date().isAfter(originalFrom)) {
      versions.addFirst(Version.original(originalFrom, id));
    }
    return new Timeline(versions);
  }

  private static List<String> distinct(SparqlResult result, String variable) {
    return result.rows().stream()
        .flatMap(r -> r.find(variable).stream())
        .distinct()
        .sorted()
        .toList();
  }

  private static List<LocalDate> dates(SparqlResult result, String variable) {
    return distinct(result, variable).stream().map(LocalDate::parse).toList();
  }

  /** An xsd:boolean as SPARQL JSON writes it ("true", or Virtuoso's "1"). */
  private static boolean isTrue(@Nullable String value) {
    return "true".equals(value) || "1".equals(value);
  }
}
