// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.TEXT_PROGRESS;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.TextProgressStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.TextProgressRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Stores the texts in the Postgres table {@code text_progress}, with jOOQ. Each change of progress
 * is a conditional update.
 */
@Repository
class JooqTextProgressStore implements TextProgressStore {
  private final DSLContext db;

  JooqTextProgressStore(DSLContext db) {
    this.db = db;
  }

  /** How far a text is, as the column {@code text_progress.progress} holds it. */
  private enum Progress {
    QUEUED,
    BUILDING,
    BUILT,
    FAILED
  }

  @Override
  public Map<String, Text> texts(
      Collection<String> celexes, Language language, Map<String, TextId> stored) {
    return db.selectFrom(TEXT_PROGRESS)
        .where(TEXT_PROGRESS.CELEX.in(celexes), TEXT_PROGRESS.LANGUAGE_CODE.eq(language))
        .fetchMap(TEXT_PROGRESS.CELEX, row -> text(row, stored.get(row.getCelex())));
  }

  @Override
  public List<String> queue(Collection<String> celexes, Language language) {
    if (celexes.isEmpty()) {
      return List.of();
    }
    var insert =
        db.insertInto(
            TEXT_PROGRESS,
            TEXT_PROGRESS.CELEX,
            TEXT_PROGRESS.LANGUAGE_CODE,
            TEXT_PROGRESS.PROGRESS,
            TEXT_PROGRESS.ATTEMPT);
    for (var celex : celexes) {
      insert = insert.values(celex, language, Progress.QUEUED.name(), 0);
    }
    return insert.onConflictDoNothing().returning(TEXT_PROGRESS.CELEX).fetch(TEXT_PROGRESS.CELEX);
  }

  @Override
  public boolean start(String celex, Language language, int attempt) {
    return db.update(TEXT_PROGRESS)
            .set(TEXT_PROGRESS.PROGRESS, Progress.BUILDING.name())
            .set(TEXT_PROGRESS.ATTEMPT, attempt)
            .where(the(celex, language), unsettled(), TEXT_PROGRESS.ATTEMPT.lt(attempt))
            .execute()
        == 1;
  }

  @Override
  public boolean defer(String celex, Language language, int attempt) {
    return db.update(TEXT_PROGRESS)
            .set(TEXT_PROGRESS.PROGRESS, Progress.QUEUED.name())
            .set(TEXT_PROGRESS.ATTEMPT, attempt)
            .where(
                the(celex, language),
                unsettled(),
                TEXT_PROGRESS
                    .ATTEMPT
                    .lt(attempt)
                    .or(
                        TEXT_PROGRESS
                            .ATTEMPT
                            .eq(attempt)
                            .and(TEXT_PROGRESS.PROGRESS.eq(Progress.BUILDING.name()))))
            .execute()
        == 1;
  }

  @Override
  public boolean keep(String celex, Language language) {
    return db.insertInto(TEXT_PROGRESS)
            .set(TEXT_PROGRESS.CELEX, celex)
            .set(TEXT_PROGRESS.LANGUAGE_CODE, language)
            .set(TEXT_PROGRESS.PROGRESS, Progress.BUILT.name())
            .set(TEXT_PROGRESS.ATTEMPT, 0)
            .set(TEXT_PROGRESS.BUILT_AT, currentInstant())
            .onConflict(TEXT_PROGRESS.CELEX, TEXT_PROGRESS.LANGUAGE_CODE)
            .doUpdate()
            .set(TEXT_PROGRESS.PROGRESS, Progress.BUILT.name())
            .set(TEXT_PROGRESS.BUILT_AT, currentInstant())
            .set(TEXT_PROGRESS.FAILURE, val(null, TEXT_PROGRESS.FAILURE))
            .set(TEXT_PROGRESS.TRACE, val(null, TEXT_PROGRESS.TRACE))
            .where(TEXT_PROGRESS.PROGRESS.ne(Progress.BUILT.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean requeue(String celex, Language language) {
    return db.update(TEXT_PROGRESS)
            .set(TEXT_PROGRESS.PROGRESS, Progress.QUEUED.name())
            .set(TEXT_PROGRESS.ATTEMPT, 0)
            .set(TEXT_PROGRESS.FAILURE, val(null, TEXT_PROGRESS.FAILURE))
            .set(TEXT_PROGRESS.TRACE, val(null, TEXT_PROGRESS.TRACE))
            .where(the(celex, language), TEXT_PROGRESS.PROGRESS.eq(Progress.FAILED.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean fail(String celex, Language language, Failure failure, @Nullable String trace) {
    return db.insertInto(TEXT_PROGRESS)
            .set(TEXT_PROGRESS.CELEX, celex)
            .set(TEXT_PROGRESS.LANGUAGE_CODE, language)
            .set(TEXT_PROGRESS.PROGRESS, Progress.FAILED.name())
            .set(TEXT_PROGRESS.ATTEMPT, 0)
            .set(TEXT_PROGRESS.FAILURE, failure)
            .set(TEXT_PROGRESS.TRACE, trace)
            .onConflict(TEXT_PROGRESS.CELEX, TEXT_PROGRESS.LANGUAGE_CODE)
            .doUpdate()
            .set(TEXT_PROGRESS.PROGRESS, Progress.FAILED.name())
            .set(TEXT_PROGRESS.FAILURE, failure)
            .set(TEXT_PROGRESS.TRACE, trace)
            .where(unsettled())
            .execute()
        == 1;
  }

  private static Condition the(String celex, Language language) {
    return TEXT_PROGRESS.CELEX.eq(celex).and(TEXT_PROGRESS.LANGUAGE_CODE.eq(language));
  }

  /** Matches a text that is queued or building. Such a text is not built and has not failed. */
  private static Condition unsettled() {
    return TEXT_PROGRESS.PROGRESS.in(Progress.QUEUED.name(), Progress.BUILDING.name());
  }

  private static Text text(TextProgressRecord row, @Nullable TextId stored) {
    return switch (Progress.valueOf(row.getProgress())) {
      case QUEUED -> new Text.Waiting(row.getAttempt());
      case BUILDING -> new Text.Converting(row.getAttempt());
      case BUILT -> stored == null ? new Text.Waiting(0) : new Text.Ready(stored);
      case FAILED -> new Text.Failed(Objects.requireNonNull(row.getFailure()), row.getTrace());
    };
  }
}
