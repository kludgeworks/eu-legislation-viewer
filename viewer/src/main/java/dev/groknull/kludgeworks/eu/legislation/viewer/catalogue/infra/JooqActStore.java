// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_AMENDMENT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_DATE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_PUBLICATION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_PUBLICATION_TITLE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_TITLE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.EXPRESSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.PREPARATION_STEP;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.TEXT_STEP_COUNT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.VERSION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.VERSION_FORMEX;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication.Journal;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActAmendmentRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActDateRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActPublicationRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.VersionFormexRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.VersionRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the acts in Postgres with jOOQ. The schema is in {@code db/migration/catalogue}. For each
 * act it stores the titles, versions with their Formex in each language, dates and amendments. It
 * also stores how the act and the acts it integrates were published.
 *
 * <ul>
 *   <li>{@link #add} writes all of it in one transaction, unless the act is already there.
 *   <li>{@link #update} writes it again when the viewer reads the act from Cellar again. It keeps
 *       the versions that Cellar no longer lists, marked missing, and drops the step counts of the
 *       act, which {@code StepCounts} keeps.
 *   <li>{@link #withdraw} deletes a version, with what the catalogue keeps of its texts.
 * </ul>
 */
@Repository
class JooqActStore implements ActStore {
  private final DSLContext db;

  JooqActStore(DSLContext db) {
    this.db = db;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Act> find(ActIdentifier id) {
    return find(ACT.CELEX.eq(id));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Act> find(Eli eli) {
    return find(ACT.ELI.eq(eli));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Act> findByVersion(String celex) {
    return find(ACT.CELEX.in(select(VERSION.ACT).from(VERSION).where(VERSION.CELEX.eq(celex))));
  }

  private Optional<Act> find(Condition which) {
    return db.selectFrom(ACT)
        .where(which)
        .fetchOptional()
        .map(
            act ->
                act(
                    act,
                    db.select(ACT_TITLE.LANGUAGE_CODE, ACT_TITLE.TITLE)
                        .from(ACT_TITLE)
                        .where(ACT_TITLE.ACT.eq(act.getCelex()))
                        .fetchMap(ACT_TITLE.LANGUAGE_CODE, ACT_TITLE.TITLE),
                    versions(act.getCelex()),
                    db.selectFrom(ACT_DATE)
                        .where(ACT_DATE.ACT.eq(act.getCelex()))
                        .orderBy(ACT_DATE.SUBJECT, ACT_DATE.ORDINAL)
                        .fetch(),
                    db.selectFrom(ACT_AMENDMENT)
                        .where(ACT_AMENDMENT.ACT.eq(act.getCelex()))
                        .orderBy(ACT_AMENDMENT.ORDINAL)
                        .fetch(JooqActStore::amendment),
                    publications(act.getCelex())));
  }

  /** Reads the versions of {@code act}, each with its Formex in each language. */
  private List<Version> versions(ActIdentifier act) {
    var formex = new HashMap<String, Map<Language, Version.Formex>>();
    for (var row : db.selectFrom(VERSION_FORMEX).where(VERSION_FORMEX.ACT.eq(act)).fetch()) {
      formex
          .computeIfAbsent(row.getCelex(), each -> new EnumMap<>(Language.class))
          .put(row.getLanguageCode(), new Version.Formex(row.getFixity(), row.getModified()));
    }
    return db.selectFrom(VERSION)
        .where(VERSION.ACT.eq(act))
        .fetch(row -> version(row, formex.getOrDefault(row.getCelex(), Map.of())));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, ActIdentifier> held(Collection<String> celexes) {
    if (celexes.isEmpty()) {
      return Map.of();
    }
    // One array parameter, however many CELEX numbers a scan read
    var listed = val(celexes.toArray(String[]::new));
    var held = new HashMap<String, ActIdentifier>();
    db.select(VERSION.CELEX, VERSION.ACT)
        .from(VERSION)
        .where(VERSION.CELEX.eq(any(listed)))
        .forEach(row -> held.put(row.value1(), row.value2()));
    db.select(ACT.CELEX)
        .from(ACT)
        .where(ACT.CELEX.cast(String.class).eq(any(listed)))
        .forEach(row -> held.put(row.value1().celex(), row.value1()));
    return held;
  }

  @Override
  @Transactional
  public boolean add(Act act) {
    var added =
        db.insertInto(ACT)
                .set(ACT.CELEX, act.id())
                .set(ACT.ELI, act.eli())
                .set(ACT.RESOURCE_TYPE, act.type())
                .set(ACT.DATE_OF_DOCUMENT, act.dateOfDocument())
                .set(ACT.IN_FORCE, act.inForce())
                .set(ACT.END_OF_VALIDITY, act.endOfValidity())
                .set(ACT.CHECKED_AT, currentInstant())
                .onConflictDoNothing()
                .execute()
            == 1;
    if (!added) {
      return false;
    }
    titles(act);
    db.batch(act.timeline().versions().stream().map(version -> version(act, version)).toList())
        .execute();
    formex(act);
    dates(act);
    amendments(act);
    publish(act);
    return true;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Instant> missing(ActIdentifier id) {
    return db.select(VERSION.CELEX, VERSION.MISSING_SINCE)
        .from(VERSION)
        .where(VERSION.ACT.eq(id), VERSION.MISSING_SINCE.isNotNull())
        .fetchMap(VERSION.CELEX, VERSION.MISSING_SINCE);
  }

  @Override
  @Transactional
  public void update(Act act, Map<String, Instant> missing) {
    db.update(ACT)
        .set(ACT.ELI, act.eli())
        .set(ACT.RESOURCE_TYPE, act.type())
        .set(ACT.DATE_OF_DOCUMENT, act.dateOfDocument())
        .set(ACT.IN_FORCE, act.inForce())
        .set(ACT.END_OF_VALIDITY, act.endOfValidity())
        .set(ACT.CHECKED_AT, currentInstant())
        .where(ACT.CELEX.eq(act.id()))
        .execute();
    db.deleteFrom(ACT_TITLE).where(ACT_TITLE.ACT.eq(act.id())).execute();
    titles(act);
    db.deleteFrom(ACT_DATE).where(ACT_DATE.ACT.eq(act.id())).execute();
    dates(act);
    db.deleteFrom(ACT_AMENDMENT).where(ACT_AMENDMENT.ACT.eq(act.id())).execute();
    amendments(act);
    db.deleteFrom(ACT_PUBLICATION_TITLE).where(ACT_PUBLICATION_TITLE.ACT.eq(act.id())).execute();
    db.deleteFrom(ACT_PUBLICATION).where(ACT_PUBLICATION.ACT.eq(act.id())).execute();
    publish(act);
    // Drop the step counts of the act, because they were counted for the act as it was before.
    db.deleteFrom(TEXT_STEP_COUNT).where(TEXT_STEP_COUNT.ACT.eq(act.id())).execute();
    db.batch(
            act.timeline().versions().stream()
                .map(
                    // An act's original text and its same-day consolidation can share a date
                    // (e.g. 32012R0965), so versions are identified by (act, celex).
                    version ->
                        version(act, version)
                            .onConflict(VERSION.ACT, VERSION.CELEX)
                            .doUpdate()
                            .set(VERSION.KIND, version.kind())
                            .set(VERSION.INTERIM, version.interim())
                            .set(VERSION.INTEGRATES, integrates(version))
                            .set(VERSION.LANGUAGES, languages(version))
                            .set(VERSION.MISSING_SINCE, val(null, VERSION.MISSING_SINCE)))
                .toList())
        .execute();
    db.deleteFrom(VERSION_FORMEX)
        .where(
            VERSION_FORMEX.ACT.eq(act.id()),
            VERSION_FORMEX.CELEX.in(
                act.timeline().versions().stream().map(Version::celex).toList()))
        .execute();
    formex(act);
    missing.forEach(
        (celex, since) ->
            db.update(VERSION)
                .set(VERSION.MISSING_SINCE, since)
                .where(VERSION.ACT.eq(act.id()), VERSION.CELEX.eq(celex))
                .execute());
  }

  @Override
  @Transactional
  public void withdraw(ActIdentifier id, String celex) {
    db.deleteFrom(PREPARATION_STEP).where(PREPARATION_STEP.CELEX.eq(celex)).execute();
    db.deleteFrom(EXPRESSION).where(EXPRESSION.CELEX.eq(celex)).execute();
    // Its Formex goes with it (on delete cascade)
    db.deleteFrom(VERSION).where(VERSION.ACT.eq(id), VERSION.CELEX.eq(celex)).execute();
  }

  /** Writes the Formex of each version of {@code act} in each language. */
  private void formex(Act act) {
    var rows = new ArrayList<VersionFormexRecord>();
    for (var version : act.timeline().versions()) {
      version
          .formex()
          .forEach(
              (language, formex) ->
                  rows.add(
                      new VersionFormexRecord(
                          act.id(),
                          version.celex(),
                          language,
                          formex.fixity(),
                          formex.modified())));
    }
    if (!rows.isEmpty()) {
      db.batchInsert(rows).execute();
    }
  }

  private void titles(Act act) {
    if (act.titles().isEmpty()) {
      return;
    }
    var titles = db.insertInto(ACT_TITLE, ACT_TITLE.ACT, ACT_TITLE.LANGUAGE_CODE, ACT_TITLE.TITLE);
    for (var title : act.titles().entrySet()) {
      titles = titles.values(act.id(), title.getKey(), title.getValue());
    }
    titles.execute();
  }

  private InsertSetMoreStep<VersionRecord> version(Act act, Version version) {
    return db.insertInto(VERSION)
        .set(VERSION.ACT, act.id())
        .set(VERSION.STARTS_ON, version.date())
        .set(VERSION.KIND, version.kind())
        .set(VERSION.CELEX, version.celex())
        .set(VERSION.INTERIM, version.interim())
        .set(VERSION.INTEGRATES, integrates(version))
        .set(VERSION.LANGUAGES, languages(version));
  }

  private static String[] integrates(Version version) {
    return version.integrates().stream().map(ActIdentifier::celex).toArray(String[]::new);
  }

  private static String[] languages(Version version) {
    return version.languages().stream().map(Language::code).toArray(String[]::new);
  }

  private void dates(Act act) {
    var dates = new LinkedHashMap<ActIdentifier, List<ActDate>>(act.amendingDates());
    dates.put(act.id(), act.dates());
    var rows =
        dates.entrySet().stream()
            .flatMap(
                subject -> {
                  var each = subject.getValue();
                  return IntStream.range(0, each.size())
                      .mapToObj(i -> date(act.id(), subject.getKey(), i, each.get(i)));
                })
            .toList();
    if (!rows.isEmpty()) {
      db.batchInsert(rows).execute();
    }
  }

  private void amendments(Act act) {
    var each = act.amendments();
    if (!each.isEmpty()) {
      db.batchInsert(
              IntStream.range(0, each.size())
                  .mapToObj(i -> amendment(act.id(), i, each.get(i)))
                  .toList())
          .execute();
    }
  }

  /** Writes how {@code act} and the acts it integrates were published, with their titles. */
  private void publish(Act act) {
    if (act.publications().isEmpty()) {
      return;
    }
    var rows = new ArrayList<ActPublicationRecord>();
    act.publications()
        .forEach(
            (subject, publication) -> {
              var journal = publication.journal();
              rows.add(
                  new ActPublicationRecord(
                      act.id(),
                      subject,
                      publication.eli(),
                      journal == null ? null : journal.series(),
                      journal == null ? null : journal.number(),
                      journal == null ? null : journal.issue(),
                      journal == null ? null : journal.published()));
            });
    db.batchInsert(rows).execute();
    var titles =
        db.insertInto(
            ACT_PUBLICATION_TITLE,
            ACT_PUBLICATION_TITLE.ACT,
            ACT_PUBLICATION_TITLE.SUBJECT,
            ACT_PUBLICATION_TITLE.LANGUAGE_CODE,
            ACT_PUBLICATION_TITLE.TITLE);
    var any = false;
    for (var publication : act.publications().entrySet()) {
      for (var title : publication.getValue().titles().entrySet()) {
        titles = titles.values(act.id(), publication.getKey(), title.getKey(), title.getValue());
        any = true;
      }
    }
    if (any) {
      titles.execute();
    }
  }

  /** Reads how {@code act} and the acts it integrates were published, by act. */
  private Map<ActIdentifier, Publication> publications(ActIdentifier act) {
    var titles = new HashMap<ActIdentifier, Map<Language, String>>();
    db.selectFrom(ACT_PUBLICATION_TITLE)
        .where(ACT_PUBLICATION_TITLE.ACT.eq(act))
        .forEach(
            title ->
                titles
                    .computeIfAbsent(title.getSubject(), each -> new EnumMap<>(Language.class))
                    .put(title.getLanguageCode(), title.getTitle()));
    var publications = new LinkedHashMap<ActIdentifier, Publication>();
    for (var row : db.selectFrom(ACT_PUBLICATION).where(ACT_PUBLICATION.ACT.eq(act)).fetch()) {
      var series = row.getSeries();
      var published = row.getPublished();
      publications.put(
          row.getSubject(),
          new Publication(
              row.getEli(),
              titles.getOrDefault(row.getSubject(), Map.of()),
              series == null || published == null
                  ? null
                  : new Journal(series, row.getNumber(), row.getIssue(), published)));
    }
    return publications;
  }

  private static ActAmendmentRecord amendment(
      ActIdentifier act, int position, Amendment amendment) {
    return new ActAmendmentRecord(
        act,
        position,
        amendment.act(),
        amendment.location(),
        amendment.role(),
        amendment.from(),
        amendment.until(),
        amendment.languages().stream().map(Language::code).sorted().toArray(String[]::new));
  }

  private static Amendment amendment(ActAmendmentRecord amendment) {
    return new Amendment(
        amendment.getAmending(),
        amendment.getCellarLocation(),
        amendment.getKind(),
        amendment.getValidFrom(),
        amendment.getValidUntil(),
        Arrays.stream(amendment.getLanguages()).map(Language::valueOf).collect(Collectors.toSet()));
  }

  private static ActDateRecord date(
      ActIdentifier act, ActIdentifier subject, int position, ActDate date) {
    var clause = date.clause();
    return new ActDateRecord(
        act,
        subject,
        position,
        date.date(),
        date.kind(),
        date.partial(),
        clause.from(),
        clause.days(),
        clause.provisions().stream().map(Provision::toString).toArray(String[]::new),
        clause.amendedBy().stream().map(ActIdentifier::celex).toArray(String[]::new),
        clause.text());
  }

  private static Act act(
      ActRecord act,
      Map<Language, String> titles,
      List<Version> versions,
      List<ActDateRecord> dates,
      List<Amendment> amendments,
      Map<ActIdentifier, Publication> publications) {
    var bySubject =
        dates.stream()
            .collect(
                Collectors.groupingBy(
                    ActDateRecord::getSubject,
                    LinkedHashMap::new,
                    Collectors.mapping(JooqActStore::date, Collectors.toList())));
    var own = bySubject.remove(act.getCelex());
    return new Act(
        act.getCelex(),
        act.getEli(),
        act.getResourceType(),
        titles,
        act.getDateOfDocument(),
        act.getInForce(),
        act.getEndOfValidity(),
        new Timeline(versions),
        own == null ? List.of() : own,
        bySubject,
        amendments,
        publications);
  }

  private static ActDate date(ActDateRecord date) {
    return new ActDate(
        date.getFallsOn(),
        date.getKind(),
        date.getInPart(),
        new Clause(
            date.getAnchor(),
            date.getDaysAfter(),
            Arrays.stream(date.getProvisions()).map(Provision::parse).toList(),
            Arrays.stream(date.getAmendedBy()).map(ActIdentifier::new).toList(),
            date.getRemark()));
  }

  private static Version version(VersionRecord version, Map<Language, Version.Formex> formex) {
    return new Version(
        version.getKind(),
        version.getStartsOn(),
        version.getCelex(),
        version.getInterim(),
        Arrays.stream(version.getIntegrates()).map(ActIdentifier::new).toList(),
        Arrays.stream(version.getLanguages()).map(Language::valueOf).collect(Collectors.toSet()),
        formex);
  }
}
