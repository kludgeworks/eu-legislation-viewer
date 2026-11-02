// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.VERSION;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ActStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.VersionRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The acts in Postgres (db/migration/catalogue), with jOOQ: an act and its versions, written in one
 * transaction, unless the act is there.
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
                    db.selectFrom(VERSION)
                        .where(VERSION.ACT.eq(act.getCelex()))
                        .fetch(JooqActStore::version)));
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
    return true;
  }

  private static Act act(ActRecord act, List<Version> versions) {
    return new Act(
        act.getCelex(),
        act.getEli(),
        act.getResourceType(),
        act.getDateOfDocument(),
        act.getInForce(),
        act.getEndOfValidity(),
        new Timeline(versions));
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
