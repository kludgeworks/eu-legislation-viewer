// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Acts as Cellar has them, from its RDF (the CDM ontology): an act's work by its CELEX, found from
 * its ELI if need be, the consolidations based on it (Research: CDM ontology), and the languages
 * each version has its text in (Research: which languages a version is in), the dates of the act
 * and of the acts its versions integrate (CellarDates), its titles, and what other acts did to its
 * provisions (CellarAmendments). Seven queries, beside this class: act.rq, act-by-eli.rq,
 * versions.rq, languages.rq, dates.rq, titles.rq and amendments.rq; an act's six side by side.
 */
@Component
class CellarActs implements ActSource {
  private static final Logger LOG = LoggerFactory.getLogger(CellarActs.class);
  private static final String ACT = Sparql.template(CellarActs.class, "act.rq");
  private static final String ACT_BY_ELI = Sparql.template(CellarActs.class, "act-by-eli.rq");
  private static final String VERSIONS = Sparql.template(CellarActs.class, "versions.rq");
  private static final String LANGUAGES = Sparql.template(CellarActs.class, "languages.rq");
  private static final String DATES = Sparql.template(CellarActs.class, "dates.rq");
  private static final String TITLES = Sparql.template(CellarActs.class, "titles.rq");
  private static final String AMENDMENTS = Sparql.template(CellarActs.class, "amendments.rq");

  /** Cellar's date for one it doesn't know (seen as an entry into force). */
  private static final LocalDate UNKNOWN = LocalDate.of(1001, 1, 1);

  /** Cellar's end of validity for an act that is open-ended. */
  private static final LocalDate OPEN = LocalDate.of(9999, 12, 31);

  private static final ResourceType TREATY = new ResourceType("TREATY");

  private final Sparql sparql;

  CellarActs(Sparql sparql) {
    this.sparql = sparql;
  }

  @Override
  public Optional<Act> find(ActIdentifier id) {
    var started = System.nanoTime();
    var values = Map.of("celex", id.celex());
    var work = select(ACT, values);
    var versions = select(VERSIONS, values);
    var languages = select(LANGUAGES, values);
    var dates = select(DATES, values);
    var titles = select(TITLES, values);
    var amendments = select(AMENDMENTS, values);
    var act =
        act(
            id,
            Sparql.join(work),
            Sparql.join(versions),
            Sparql.join(languages),
            Sparql.join(dates),
            Sparql.join(titles),
            Sparql.join(amendments));
    var took = Duration.ofNanos(System.nanoTime() - started).toMillis();
    act.ifPresentOrElse(
        a ->
            LOG.info(
                "Read {} from Cellar: {} versions, titles in {} languages, {} dates of its own,"
                    + " dates of {} amending acts, {} amended locations, in {} ms",
                id,
                a.timeline().versions().size(),
                a.titles().size(),
                a.dates().size(),
                a.amendingDates().size(),
                a.amendments().size(),
                took),
        () -> LOG.info("{} isn't in Cellar ({} ms)", id, took));
    return act;
  }

  /** The query {@code template} with {@code values}, asked of Cellar. */
  private CompletableFuture<SparqlResult> select(String template, Map<String, String> values) {
    return sparql.answer(Sparql.fill(template, values));
  }

  @Override
  public Optional<Act> find(Eli eli) {
    var id = identity(eli, sparql.select(Sparql.fill(ACT_BY_ELI, Map.of("eli", eli.path()))));
    LOG.debug("ELI {} is {}", eli, id.map(ActIdentifier::celex).orElse("no act in Cellar"));
    return id.flatMap(this::find);
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
   * The act from the answers to act.rq, versions.rq, languages.rq, dates.rq, titles.rq and
   * amendments.rq; none if act.rq found no work.
   *
   * @throws CellarException if Cellar's answers make no act: a value missing, repeated or not of
   *     its kind, or versions the timeline refuses
   */
  static Optional<Act> act(
      ActIdentifier id,
      SparqlResult work,
      SparqlResult consolidations,
      SparqlResult languages,
      SparqlResult dates,
      SparqlResult titles,
      SparqlResult amendments) {
    if (work.rows().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          read(
              id,
              work,
              titles(titles),
              consolidations,
              formexByVersion(languages),
              CellarDates.byAct(id, dates),
              CellarAmendments.of(id, amendments)));
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new CellarException("Cellar's data for " + id + ": " + e.getMessage(), e);
    }
  }

  /** The act; an IllegalArgumentException (or a DateTimeException) for what Cellar got wrong. */
  private static Act read(
      ActIdentifier id,
      SparqlResult work,
      Map<Language, String> titles,
      SparqlResult consolidations,
      Map<String, Formex> formex,
      Map<ActIdentifier, List<ActDate>> dates,
      List<Amendment> amendments) {
    var row = work.rows().getFirst();
    var types = distinct(work, "type");
    if (types.size() != 1) {
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
    var type = ResourceType.ofUri(types.getFirst());
    // a treaty edition's entry into force is the treaty's (1958 for the TFEU of 2016): its text is
    // the edition's from its date, earlier editions being acts of their own
    var originalFrom =
        type.equals(TREATY) && dateOfDocument != null
            ? dateOfDocument
            : entryIntoForce.orElse(dateOfDocument);
    if (originalFrom == null) {
      throw new IllegalArgumentException("neither an entry into force nor a date of document");
    }
    var timeline = timeline(id, originalFrom, consolidations, formex);
    var integrated = new HashSet<ActIdentifier>();
    timeline.versions().forEach(v -> integrated.addAll(v.integrates()));
    var amendingDates = new HashMap<>(dates);
    amendingDates.keySet().retainAll(integrated);
    return new Act(
        id,
        elis.isEmpty() ? null : eli(id, elis.getFirst()),
        type,
        titles,
        dateOfDocument,
        row.find("inForce").map(CellarActs::isTrue).orElse(false),
        endOfValidity.orElse(null),
        timeline,
        dates.getOrDefault(id, List.of()),
        amendingDates,
        amendments);
  }

  /**
   * The act's ELI; none, with a warning, for one that isn't below http://data.europa.eu/eli/: the
   * act is still read, by its CELEX.
   */
  private static @Nullable Eli eli(ActIdentifier id, String uri) {
    var eli = Eli.parse(uri);
    if (eli.isEmpty()) {
      LOG.warn("{}'s ELI isn't one, so it is read without it: {}", id, uri);
    }
    return eli.orElse(null);
  }

  /**
   * The original text, then the consolidated versions in date order. A consolidation that starts on
   * the original's own date replaces it, as it's the later text of that day. Each with the Formex
   * {@code formex} has for its CELEX; none if it has none.
   */
  static Timeline timeline(
      ActIdentifier id,
      LocalDate originalFrom,
      SparqlResult consolidations,
      Map<String, Formex> formex) {
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
      var texts = formex.getOrDefault(first.get("version"), Formex.NONE);
      versions.add(
          new Version(
              Version.Kind.CONSOLIDATED,
              LocalDate.parse(first.get("date")),
              first.get("version"),
              !isTrue(first.get("pdf")),
              integrates.stream().map(ActIdentifier::new).toList(),
              texts.languages(),
              texts.modified()));
    }
    versions.sort(Comparator.comparing(Version::date));
    if (versions.isEmpty() || versions.getFirst().date().isAfter(originalFrom)) {
      var texts = formex.getOrDefault(id.celex(), Formex.NONE);
      versions.addFirst(Version.original(originalFrom, id, texts.languages(), texts.modified()));
    }
    return new Timeline(versions);
  }

  /** A version's Formex: its languages, and when Cellar last modified it; none without. */
  record Formex(Set<Language> languages, @Nullable Instant modified) {
    static final Formex NONE = new Formex(Set.of(), null);
  }

  /**
   * The answer to languages.rq as the Formex of each version, by its CELEX: the key versions.rq
   * gives a version (Version.celex), to join the two answers on. Official languages only (Cellar
   * has expressions in others, as Norwegian for the EEA).
   */
  private static Map<String, Formex> formexByVersion(SparqlResult answer) {
    var formex = new HashMap<String, Formex>();
    for (var row : answer.rows()) {
      var listed = Set.of(row.get("languages").split(" "));
      var official = EnumSet.noneOf(Language.class);
      Arrays.stream(Language.values())
          .filter(language -> listed.contains(language.code()))
          .forEach(official::add);
      if (!official.isEmpty()) {
        formex.put(
            row.get("version"),
            new Formex(official, OffsetDateTime.parse(row.get("formexModified")).toInstant()));
      }
    }
    return formex;
  }

  /**
   * The answer to titles.rq as the act's titles, by language: official languages only. Two titles
   * in one language are an IllegalArgumentException.
   */
  private static Map<Language, String> titles(SparqlResult answer) {
    var titles = new EnumMap<Language, String>(Language.class);
    for (var row : answer.rows()) {
      var code = row.get("code");
      Arrays.stream(Language.values())
          .filter(language -> language.code().equals(code))
          .findFirst()
          .ifPresent(
              language -> {
                if (titles.put(language, row.get("title")) != null) {
                  throw new IllegalArgumentException("several titles in " + code);
                }
              });
    }
    return titles;
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
