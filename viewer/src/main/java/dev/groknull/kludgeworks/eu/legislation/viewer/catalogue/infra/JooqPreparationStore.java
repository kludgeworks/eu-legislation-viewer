// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.PREPARATION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.PREPARATION_ENTRY;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Entry;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.PreparationStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.PreparationRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Stores the preparations in the Postgres tables {@code preparation} and {@code preparation_entry},
 * with jOOQ.
 */
@Repository
class JooqPreparationStore implements PreparationStore {
  private final DSLContext db;

  JooqPreparationStore(DSLContext db) {
    this.db = db;
  }

  /** How far a preparation is, as the column {@code preparation.progress} holds it. */
  private enum Progress {
    PREPARING,
    PREPARED,
    FAILED
  }

  @Override
  public void start(ActIdentifier act, Language language) {
    db.insertInto(PREPARATION)
        .set(PREPARATION.ACT, act)
        .set(PREPARATION.LANGUAGE_CODE, language)
        .set(PREPARATION.PROGRESS, Progress.PREPARING.name())
        .set(PREPARATION.STARTED_AT, currentInstant())
        .onConflictDoNothing()
        .execute();
  }

  @Override
  public List<Language> languages(ActIdentifier act) {
    return db.select(PREPARATION.LANGUAGE_CODE)
        .from(PREPARATION)
        .where(PREPARATION.ACT.eq(act))
        .fetch(PREPARATION.LANGUAGE_CODE);
  }

  @Override
  public Optional<Kept> find(ActIdentifier act, Language language) {
    return db.selectFrom(PREPARATION)
        .where(the(act, language))
        .fetchOptional()
        .map(JooqPreparationStore::kept);
  }

  @Override
  public Optional<Kept> lock(ActIdentifier act, Language language) {
    return db.selectFrom(PREPARATION)
        .where(the(act, language))
        // The key of a preparation never changes. So this lock does not conflict with the
        // foreign key of an entry that refers to the preparation.
        .forNoKeyUpdate()
        .fetchOptional()
        .map(JooqPreparationStore::kept);
  }

  @Override
  public boolean settle(ActIdentifier act, Language language, boolean prepared) {
    return db.update(PREPARATION)
            .set(PREPARATION.PROGRESS, (prepared ? Progress.PREPARED : Progress.FAILED).name())
            .set(PREPARATION.SETTLED_AT, currentInstant())
            .where(the(act, language), PREPARATION.PROGRESS.eq(Progress.PREPARING.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean restart(ActIdentifier act, Language language) {
    var restarted =
        db.update(PREPARATION)
                .set(PREPARATION.PROGRESS, Progress.PREPARING.name())
                .set(PREPARATION.STARTED_AT, currentInstant())
                .set(PREPARATION.SETTLED_AT, val(null, PREPARATION.SETTLED_AT))
                .where(the(act, language), PREPARATION.PROGRESS.ne(Progress.PREPARING.name()))
                .execute()
            == 1;
    if (restarted) {
      db.deleteFrom(PREPARATION_ENTRY)
          .where(PREPARATION_ENTRY.ACT.eq(act), PREPARATION_ENTRY.LANGUAGE_CODE.eq(language))
          .execute();
    }
    return restarted;
  }

  @Override
  public void entry(
      ActIdentifier act,
      Language language,
      String celex,
      Entry.Kind kind,
      @Nullable Integer attempt,
      @Nullable Failure failure) {
    db.insertInto(
            PREPARATION_ENTRY,
            PREPARATION_ENTRY.ACT,
            PREPARATION_ENTRY.LANGUAGE_CODE,
            PREPARATION_ENTRY.HAPPENED_AT,
            PREPARATION_ENTRY.CELEX,
            PREPARATION_ENTRY.KIND,
            PREPARATION_ENTRY.ATTEMPT,
            PREPARATION_ENTRY.FAILURE)
        .select(
            select(
                    val(act, PREPARATION_ENTRY.ACT),
                    val(language, PREPARATION_ENTRY.LANGUAGE_CODE),
                    currentInstant(),
                    val(celex),
                    val(kind.name()),
                    val(attempt, PREPARATION_ENTRY.ATTEMPT),
                    val(failure, PREPARATION_ENTRY.FAILURE))
                .whereExists(selectOne().from(PREPARATION).where(the(act, language))))
        .execute();
  }

  @Override
  public List<Entry> entries(ActIdentifier act, Language language) {
    var rows =
        db.selectFrom(PREPARATION_ENTRY)
            .where(PREPARATION_ENTRY.ACT.eq(act), PREPARATION_ENTRY.LANGUAGE_CODE.eq(language))
            .orderBy(PREPARATION_ENTRY.HAPPENED_AT)
            .fetch();
    // The last start of each text, which gives its attempt and its start time.
    var starts = new HashMap<String, Entry>();
    var entries = new ArrayList<Entry>(rows.size());
    for (var row : rows) {
      var kind = Entry.Kind.valueOf(row.getKind());
      var at = row.getHappenedAt();
      var celex = row.getCelex();
      var start = starts.get(celex);
      var entry =
          switch (kind) {
            case STARTED, POSTPONED ->
                new Entry(at, celex, kind, attempt(row.getAttempt()), null, null);
            case BUILT, FAILED ->
                new Entry(
                    at,
                    celex,
                    kind,
                    start == null ? 0 : start.attempt(),
                    start == null ? null : Duration.between(start.at(), at),
                    row.getFailure());
          };
      if (kind == Entry.Kind.STARTED) {
        starts.put(celex, entry);
      }
      entries.add(entry);
    }
    return entries;
  }

  private static int attempt(@Nullable Integer attempt) {
    return attempt == null ? 0 : attempt;
  }

  private static Condition the(ActIdentifier act, Language language) {
    return PREPARATION.ACT.eq(act).and(PREPARATION.LANGUAGE_CODE.eq(language));
  }

  private static Kept kept(PreparationRecord row) {
    return new Kept(row.getStartedAt(), row.getSettledAt());
  }
}
