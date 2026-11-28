// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheVersionsOfAnAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActSource;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.Sparql;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
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
 *   <li>how the act, the acts its versions integrate and the acts that set a day of its dates were
 *       published, with {@link CellarPublications}.
 * </ul>
 *
 * <p>It uses ten queries, kept next to this class: {@code act.rq}, {@code act-by-eli.rq}, {@code
 * act-by-version.rq}, {@code versions.rq}, {@code languages.rq}, {@code dates.rq}, {@code
 * titles.rq}, {@code amendments.rq}, {@code publications.rq} and {@code publications-of.rq}. All of
 * them except the two that find an act by another identifier, and {@code publications-of.rq}, run
 * at the same time for one act. {@code publications-of.rq} runs after {@code dates.rq}, and only if
 * an act sets a day of a date ({@link CellarDates#settingActs}).
 */
@Component
class CellarActs implements ActSource {
  private static final Logger LOG = LoggerFactory.getLogger(CellarActs.class);
  private static final String ACT_BY_ELI = Sparql.template(CellarActs.class, "act-by-eli.rq");
  private static final String ACT_BY_VERSION =
      Sparql.template(CellarActs.class, "act-by-version.rq");
  private static final String PUBLICATIONS_OF =
      Sparql.template(CellarActs.class, "publications-of.rq");

  /** An answer with no rows, for a query that need not run. */
  private static final SparqlResult NONE = new SparqlResult(List.of(), List.of());

  /** The date Cellar uses when it does not know a date. It occurs as an entry into force. */
  private static final LocalDate UNKNOWN = LocalDate.of(1001, 1, 1);

  /** Cellar's end of validity for an act that is open-ended. */
  private static final LocalDate OPEN = LocalDate.of(9999, 12, 31);

  private static final ResourceType TREATY = new ResourceType("TREATY");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final Sparql sparql;
  private final CellarDates.EnactedDateLookup enactedDates;

  CellarActs(Sparql sparql, CellarDates.EnactedDateLookup enactedDates) {
    this.sparql = sparql;
    this.enactedDates = enactedDates;
  }

  @Override
  public Optional<Act> find(ActIdentifier id) {
    var started = System.nanoTime();
    var values = Map.of("celex", id.celex());
    var work = select(ActQuery.ACT, values);
    var versions = select(ActQuery.VERSIONS, values);
    var languages = select(ActQuery.LANGUAGES, values);
    var dates = select(ActQuery.DATES, values);
    var titles = select(ActQuery.TITLES, values);
    var amendments = select(ActQuery.AMENDMENTS, values);
    var publications = select(ActQuery.PUBLICATIONS, values);
    var setting = dates.thenCompose(this::publicationsOfSettingActs);
    var act =
        act(
            id,
            Sparql.join(work),
            Sparql.join(versions),
            Sparql.join(languages),
            Sparql.join(dates),
            Sparql.join(titles),
            Sparql.join(amendments),
            Sparql.join(publications),
            Sparql.join(setting),
            enactedDates);
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
                a.dates().own().size(),
                a.dates().amending().size(),
                a.amendments().size(),
                a.publications().size(),
                took),
        () -> LOG.info("{} isn't in Cellar ({} ms)", id, took));
    return act;
  }

  private enum ActQuery {
    ACT,
    VERSIONS,
    LANGUAGES,
    DATES,
    TITLES,
    AMENDMENTS,
    PUBLICATIONS;

    final String templateName = name().toLowerCase(Locale.ROOT);
    final String template = Sparql.template(CellarActs.class, templateName + ".rq");
  }

  /**
   * Asks Cellar how the acts that set a day of a date in {@code dates} were published. It asks
   * nothing if no act sets a day.
   */
  private CompletableFuture<SparqlResult> publicationsOfSettingActs(SparqlResult dates) {
    var acts = CellarDates.settingActs(dates).stream().map(ActIdentifier::celex).toList();
    return acts.isEmpty()
        ? CompletableFuture.completedFuture(NONE)
        : sparql.answer(
            Sparql.fillStrings(PUBLICATIONS_OF, Map.of("celexes", acts)), "publications-of");
  }

  /** Fills the query template with {@code values} and sends it to Cellar. */
  private CompletableFuture<SparqlResult> select(ActQuery query, Map<String, String> values) {
    return sparql.answer(Sparql.fill(query.template, values), query.templateName);
  }

  @Override
  public Optional<Act> find(Eli eli) {
    var id =
        identity(
            "the ELI " + eli, sparql.select(Sparql.fill(ACT_BY_ELI, Map.of("eli", eli.path()))));
    LOG.debug("ELI {} is {}", eli, id.map(ActIdentifier::celex).orElse("no act in Cellar"));
    return id.flatMap(this::find);
  }

  @Override
  public Optional<Act> findByVersion(String celex) {
    var id =
        identity(
            "the version " + celex,
            sparql.select(Sparql.fill(ACT_BY_VERSION, Map.of("version", celex))));
    LOG.debug(
        "The version {} is of {}", celex, id.map(ActIdentifier::celex).orElse("no act in Cellar"));
    return id.flatMap(this::find);
  }

  /**
   * Reads the act from the answer to {@code act-by-eli.rq} or {@code act-by-version.rq}. It returns
   * none if no work has the identifier. {@code named} says what was asked, for example "the ELI
   * reg_impl/2015/1998".
   *
   * @throws CellarException if the answer does not give one act, for example because several acts
   *     have the identifier
   */
  static Optional<ActIdentifier> identity(String named, SparqlResult works) {
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
      throw new CellarException("Cellar's data for " + named + ": " + e.getMessage(), e);
    }
  }

  /**
   * Reads the act from the answers to {@code act.rq}, {@code versions.rq}, {@code languages.rq},
   * {@code dates.rq}, {@code titles.rq}, {@code amendments.rq} and {@code publications.rq}, where
   * no act sets a day of a date. It returns none if {@code act.rq} found no work.
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
      SparqlResult publications,
      CellarDates.EnactedDateLookup enacted) {
    return act(
        id,
        work,
        consolidations,
        languages,
        dates,
        titles,
        amendments,
        publications,
        NONE,
        enacted);
  }

  /**
   * Reads the act from the answers to {@code act.rq}, {@code versions.rq}, {@code languages.rq},
   * {@code dates.rq}, {@code titles.rq}, {@code amendments.rq}, {@code publications.rq} and {@code
   * publications-of.rq} ({@code setting}). It returns none if {@code act.rq} found no work.
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
      SparqlResult publications,
      SparqlResult setting,
      CellarDates.EnactedDateLookup enacted) {
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
              publications(publications, setting),
              enacted));
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new CellarException("Cellar's data for " + id + ": " + e.getMessage(), e);
    }
  }

  /** Returns the publications in the answers to {@code publications.rq} and {@code setting}. */
  private static Map<ActIdentifier, Publication> publications(
      SparqlResult publications, SparqlResult setting) {
    var all = new HashMap<>(CellarPublications.byAct(publications));
    CellarPublications.byAct(setting).forEach(all::putIfAbsent);
    return all;
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
      Map<String, Map<Language, Version.Formex>> formex,
      Map<ActIdentifier, List<ActDate>> rawDates,
      List<Amendment> rawAmendments,
      Map<ActIdentifier, Publication> publications,
      CellarDates.EnactedDateLookup enacted) {
    var checked = CellarDates.checkAgainstPublication(rawDates, publications, enacted);
    var dates = checked.dates();
    var amendments = reconcileAmendments(rawAmendments, checked.corrections());
    var row = work.rows().getFirst();
    var types = distinct(work, "type");
    if (types.size() != 1) {
      throw new IllegalArgumentException("resource types " + types);
    }
    var elis = distinct(work, "eli");
    if (elis.size() > 1) {
      throw new IllegalArgumentException("several ELIs: " + elis);
    }
    var checkedEntryIntoForce =
        dates.getOrDefault(id, List.of()).stream()
            .filter(d -> d.kind() == ActDate.Kind.ENTRY_INTO_FORCE && d.date() != null)
            .map(ActDate::date)
            .min(Comparator.naturalOrder());
    var rawEntryIntoForce =
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
            : checkedEntryIntoForce.or(() -> rawEntryIntoForce).orElse(dateOfDocument);
    if (originalFrom == null) {
      throw new IllegalArgumentException("neither an entry into force nor a date of document");
    }
    var timeline = timeline(id, originalFrom, dateOfDocument, consolidations, formex);
    // The acts that the versions integrate, and the acts that amend the act. An act that amends it
    // can be in no version yet.
    var amending = new HashSet<ActIdentifier>();
    timeline.versions().forEach(v -> amending.addAll(v.integrates()));
    amendments.forEach(amendment -> amending.add(amendment.act()));
    var amendingDates = new HashMap<>(dates);
    amendingDates.keySet().retainAll(amending);
    var published = new HashMap<>(publications);
    // Keep the publications of those acts, and of the acts that set a day of their dates or of the
    // act's own dates, for their citations. Also keep the publication of the act itself, for the
    // act as published.
    var kept = new HashSet<>(amending);
    amendingDates
        .values()
        .forEach(each -> each.forEach(date -> kept.addAll(date.clause().setBy())));
    dates.getOrDefault(id, List.of()).forEach(date -> kept.addAll(date.clause().setBy()));
    published.keySet().retainAll(kept);
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
        distinct(work, "repealedBy").stream().map(ActIdentifier::new).toList(),
        timeline,
        new ActDates(id, dates.getOrDefault(id, List.of()), amendingDates),
        amendments,
        published);
  }

  private record AmbiguousDay(ActIdentifier act, LocalDate day) {}

  private static List<Amendment> reconcileAmendments(
      List<Amendment> rawAmendments,
      Map<ActIdentifier, Map<LocalDate, Optional<LocalDate>>> corrections) {
    if (corrections.isEmpty() || rawAmendments.isEmpty()) {
      return rawAmendments;
    }
    var reconciled = new ArrayList<Amendment>(rawAmendments.size());
    var leftUnchanged = new LinkedHashMap<AmbiguousDay, Integer>();
    for (var amendment : rawAmendments) {
      var from = amendment.from();
      if (from == null) {
        reconciled.add(amendment);
        continue;
      }
      var mapping = corrections.get(amendment.act());
      if (mapping == null || !mapping.containsKey(from)) {
        reconciled.add(amendment);
        continue;
      }
      var corrected = mapping.get(from);
      if (corrected.isEmpty()) {
        leftUnchanged.merge(new AmbiguousDay(amendment.act(), from), 1, Integer::sum);
        reconciled.add(amendment);
      } else {
        reconciled.add(amendment.withFrom(corrected.get()));
      }
    }
    leftUnchanged.forEach((ambiguous, count) ->
        LOG.info(
            "{}: leaving {} amendment(s) from {} unchanged (ambiguous date correction)",
            ambiguous.act(),
            count,
            ambiguous.day()));
    return reconciled;
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
   * Returns the timeline: the consolidated versions that Cellar lists, and the original text where
   * the act has one ({@link TheVersionsOfAnAct#theOriginalTextOfAnAct}). Each version gets the
   * Formex that {@code formex} has for its CELEX number. A version with no entry in {@code formex}
   * gets none.
   */
  static Timeline timeline(
      ActIdentifier id,
      LocalDate originalFrom,
      @Nullable LocalDate dateOfDocument,
      SparqlResult consolidations,
      Map<String, Map<Language, Version.Formex>> formex) {
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
      var texts = formex.getOrDefault(first.get("version"), Map.of());
      versions.add(
          new Version(
              Version.Kind.CONSOLIDATED,
              LocalDate.parse(first.get("date")),
              first.get("version"),
              !isTrue(first.get("pdf")),
              integrates.stream().map(ActIdentifier::new).toList(),
              texts.keySet(),
              texts));
    }
    versions.sort(Comparator.comparing(Version::date));
    TheVersionsOfAnAct.theOriginalTextOfAnAct(
            id, originalFrom, dateOfDocument, versions, formex.getOrDefault(id.celex(), Map.of()))
        .ifPresent(versions::add);
    return new Timeline(versions);
  }

  /**
   * Reads the answer to {@code languages.rq} as the Formex of each version in each language, by
   * CELEX number. The CELEX number is the key that {@code versions.rq} gives a version, {@code
   * Version.celex}, so the two answers join on it. It keeps official languages only. Cellar also
   * has expressions in other languages, for example Norwegian for the EEA.
   */
  static Map<String, Map<Language, Version.Formex>> formexByVersion(SparqlResult answer) {
    var formex = new HashMap<String, Map<Language, Version.Formex>>();
    for (var row : answer.rows()) {
      var code = row.get("language");
      var language =
          Arrays.stream(Language.values()).filter(each -> each.code().equals(code)).findFirst();
      if (language.isEmpty()) {
        continue;
      }
      formex
          .computeIfAbsent(row.get("version"), each -> new EnumMap<>(Language.class))
          .put(
              language.get(),
              new Version.Formex(
                  fixity(row.find("fixities").orElse("")),
                  OffsetDateTime.parse(row.get("formexModified")).toInstant()));
    }
    return formex;
  }

  /**
   * Returns one fixity for the SHA-1 fixities of a manifestation's files, as {@code languages.rq}
   * lists them, in any order: the SHA-256 of the sorted fixities, in hex. It changes when the
   * content of any file changes, and only then. Returns null if Cellar lists none.
   */
  static @Nullable String fixity(String listed) {
    var each = new TreeSet<String>();
    for (var fixity : WHITESPACE.split(listed.trim())) {
      if (!fixity.isEmpty()) {
        each.add(fixity.toLowerCase(Locale.ROOT));
      }
    }
    if (each.isEmpty()) {
      return null;
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(String.join(" ", each).getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has SHA-256", e);
    }
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
          .ifPresent(language -> {
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
