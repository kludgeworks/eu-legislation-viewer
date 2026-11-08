// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
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
 * Reads acts from Cellar's RDF, which uses the CDM ontology. For an act, it reads:
 *
 * <ul>
 *   <li>the work, by its CELEX number. If necessary, it finds the CELEX number from the ELI.
 *   <li>the consolidations based on the work.
 *   <li>the languages that each version has its text in.
 *   <li>the dates of the act, and of the acts that its versions integrate, with {@link
 *       CellarDates}.
 *   <li>its titles.
 *   <li>what other acts did to its provisions, with {@link CellarAmendments}.
 *   <li>how the act and the acts its versions integrate were published, with {@link
 *       CellarPublications}.
 * </ul>
 *
 * <p>It uses eight queries, kept next to this class: {@code act.rq}, {@code act-by-eli.rq}, {@code
 * versions.rq}, {@code languages.rq}, {@code dates.rq}, {@code titles.rq}, {@code amendments.rq}
 * and {@code publications.rq}. All of them except {@code act-by-eli.rq} run at the same time for
 * one act.
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
  private static final String PUBLICATIONS = Sparql.template(CellarActs.class, "publications.rq");

  /** The date Cellar uses when it does not know a date. It occurs as an entry into force. */
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
    var publications = select(PUBLICATIONS, values);
    var act =
        act(
            id,
            Sparql.join(work),
            Sparql.join(versions),
            Sparql.join(languages),
            Sparql.join(dates),
            Sparql.join(titles),
            Sparql.join(amendments),
            Sparql.join(publications));
    var took = Duration.ofNanos(System.nanoTime() - started).toMillis();
    act.ifPresentOrElse(
        a ->
            LOG.info(
                "Read {} from Cellar: {} versions, titles in {} languages, {} dates of its own,"
                    + " dates of {} amending acts, {} amended locations, {} publications, in {}"
                    + " ms",
                id,
                a.timeline().versions().size(),
                a.titles().size(),
                a.dates().size(),
                a.amendingDates().size(),
                a.amendments().size(),
                a.publications().size(),
                took),
        () -> LOG.info("{} isn't in Cellar ({} ms)", id, took));
    return act;
  }

  /** Fills the query {@code template} with {@code values} and sends it to Cellar. */
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
   * Reads the act from the answer to {@code act-by-eli.rq}. It returns none if no work has the ELI.
   *
   * @throws CellarException if the answer does not give one act, for example because several acts
   *     have the ELI
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
   * Reads the act from the answers to {@code act.rq}, {@code versions.rq}, {@code languages.rq},
   * {@code dates.rq}, {@code titles.rq}, {@code amendments.rq} and {@code publications.rq}. It
   * returns none if {@code act.rq} found no work.
   *
   * @throws CellarException if the answers do not make an act. For example, a value is missing,
   *     repeated or of the wrong kind, or the timeline refuses the versions.
   */
  static Optional<Act> act(
      ActIdentifier id,
      SparqlResult work,
      SparqlResult consolidations,
      SparqlResult languages,
      SparqlResult dates,
      SparqlResult titles,
      SparqlResult amendments,
      SparqlResult publications) {
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
              CellarAmendments.of(id, amendments),
              CellarPublications.byAct(publications)));
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new CellarException("Cellar's data for " + id + ": " + e.getMessage(), e);
    }
  }

  /**
   * Reads the act. It throws an {@code IllegalArgumentException} or a {@code DateTimeException} if
   * Cellar's data is wrong.
   */
  private static Act read(
      ActIdentifier id,
      SparqlResult work,
      Map<Language, String> titles,
      SparqlResult consolidations,
      Map<String, Formex> formex,
      Map<ActIdentifier, List<ActDate>> rawDates,
      List<Amendment> amendments,
      Map<ActIdentifier, Publication> publications) {
    var dates = CellarDates.checkAgainstPublication(rawDates, publications);
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
    // The entry into force of a treaty edition is the entry into force of the treaty. For example,
    // it is 1958 for the TFEU of 2016. The text of the edition starts on its date of document,
    // because earlier editions are separate acts.
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
    var published = new HashMap<>(publications);
    // Keep the publications of the acts that the versions integrate. Also keep the publication of
    // the act itself, for the act as published.
    published.keySet().retainAll(integrated);
    var own = publications.get(id);
    if (own != null) {
      published.put(id, own);
    }
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
        amendments,
        published);
  }

  /**
   * Returns the ELI of the act. If the URI is not below {@code http://data.europa.eu/eli/}, it logs
   * a warning and returns none. The viewer still reads the act, by its CELEX number.
   */
  private static @Nullable Eli eli(ActIdentifier id, String uri) {
    var eli = Eli.parse(uri);
    if (eli.isEmpty()) {
      LOG.warn("{}'s ELI isn't one, so it is read without it: {}", id, uri);
    }
    return eli.orElse(null);
  }

  /**
   * Returns the timeline: the original text, then the consolidated versions in date order. A
   * consolidation that starts on the date of the original replaces the original only in the
   * languages it exists in; for the remaining languages, the timeline keeps the original text. Each
   * version gets the Formex that {@code formex} has for its CELEX number. A version with no entry
   * in {@code formex} gets none.
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
    var originalTexts = formex.getOrDefault(id.celex(), Formex.NONE);
    var sameDayConsolidations =
        versions.stream().filter(v -> v.date().equals(originalFrom)).toList();
    if (sameDayConsolidations.isEmpty()) {
      if (versions.isEmpty() || versions.getFirst().date().isAfter(originalFrom)) {
        versions.add(
            Version.original(
                originalFrom, id, originalTexts.languages(), originalTexts.modified()));
      }
    } else {
      var consolidatedLanguages = EnumSet.noneOf(Language.class);
      sameDayConsolidations.forEach(v -> consolidatedLanguages.addAll(v.languages()));
      var remainingLanguages =
          originalTexts.languages().isEmpty()
              ? EnumSet.noneOf(Language.class)
              : EnumSet.copyOf(originalTexts.languages());
      remainingLanguages.removeAll(consolidatedLanguages);
      if (!remainingLanguages.isEmpty()) {
        versions.add(
            Version.original(originalFrom, id, remainingLanguages, originalTexts.modified()));
      }
    }
    return new Timeline(versions);
  }

  /**
   * The Formex of a version: its languages, and when Cellar last modified it. {@link #NONE} is for
   * a version without Formex.
   */
  record Formex(Set<Language> languages, @Nullable Instant modified) {
    static final Formex NONE = new Formex(Set.of(), null);
  }

  /**
   * Reads the answer to {@code languages.rq} as the Formex of each version, by CELEX number. The
   * CELEX number is the key that {@code versions.rq} gives a version, {@code Version.celex}, so the
   * two answers join on it. It keeps official languages only. Cellar also has expressions in other
   * languages, for example Norwegian for the EEA.
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
   * Reads the answer to {@code titles.rq} as the titles of the act, by language. It keeps official
   * languages only. It throws an {@code IllegalArgumentException} if one language has two titles.
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

  /**
   * Reads an {@code xsd:boolean} as SPARQL JSON writes it. True is {@code "true"}, or {@code "1"}
   * from Virtuoso.
   */
  private static boolean isTrue(@Nullable String value) {
    return "true".equals(value) || "1".equals(value);
  }
}
