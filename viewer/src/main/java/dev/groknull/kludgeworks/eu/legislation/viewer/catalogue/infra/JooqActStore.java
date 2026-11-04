// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_DATE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_TITLE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.VERSION;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActDateRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.VersionRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The acts in Postgres (db/migration/catalogue), with jOOQ: an act, its titles, its versions and
 * its dates, written in one transaction, unless the act is there.
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

  private Optional<Act> find(Condition which) {
    return db.selectFrom(ACT)
        .where(which)
        .fetchOptional()
        .map(
            act ->
                act(
                    act,
                    db.select(ACT_TITLE.LANGUAGE, ACT_TITLE.TITLE)
                        .from(ACT_TITLE)
                        .where(ACT_TITLE.ACT.eq(act.getCelex()))
                        .fetchMap(ACT_TITLE.LANGUAGE, ACT_TITLE.TITLE),
                    db.selectFrom(VERSION)
                        .where(VERSION.ACT.eq(act.getCelex()))
                        .fetch(JooqActStore::version),
                    db.selectFrom(ACT_DATE)
                        .where(ACT_DATE.ACT.eq(act.getCelex()))
                        .orderBy(ACT_DATE.SUBJECT, ACT_DATE.POSITION)
                        .fetch()));
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
    if (!act.titles().isEmpty()) {
      var titles = db.insertInto(ACT_TITLE, ACT_TITLE.ACT, ACT_TITLE.LANGUAGE, ACT_TITLE.TITLE);
      for (var title : act.titles().entrySet()) {
        titles = titles.values(act.id(), title.getKey(), title.getValue());
      }
      titles.execute();
    }
    var versions =
        db.insertInto(
            VERSION,
            VERSION.ACT,
            VERSION.DATE,
            VERSION.KIND,
            VERSION.CELEX,
            VERSION.INTERIM,
            VERSION.INTEGRATES,
            VERSION.LANGUAGES,
            VERSION.FORMEX_MODIFIED);
    for (var version : act.timeline().versions()) {
      versions =
          versions.values(
              act.id(),
              version.date(),
              version.kind(),
              version.celex(),
              version.interim(),
              version.integrates().stream().map(ActIdentifier::celex).toArray(String[]::new),
              version.languages().stream().map(Language::code).toArray(String[]::new),
              version.formexModified());
    }
    versions.execute();
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
    return true;
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
      List<ActDateRecord> dates) {
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
        bySubject);
  }

  private static ActDate date(ActDateRecord date) {
    return new ActDate(
        date.getDate(),
        date.getKind(),
        date.getPartial(),
        new Clause(
            date.getAnchor(),
            date.getDays(),
            Arrays.stream(date.getProvisions()).map(Provision::parse).toList(),
            Arrays.stream(date.getAmendedBy()).map(ActIdentifier::new).toList(),
            date.getText()));
  }

  private static Version version(VersionRecord version) {
    return new Version(
        version.getKind(),
        version.getDate(),
        version.getCelex(),
        version.getInterim(),
        Arrays.stream(version.getIntegrates()).map(ActIdentifier::new).toList(),
        Arrays.stream(version.getLanguages()).map(Language::valueOf).collect(Collectors.toSet()),
        version.getFormexModified());
  }
}
