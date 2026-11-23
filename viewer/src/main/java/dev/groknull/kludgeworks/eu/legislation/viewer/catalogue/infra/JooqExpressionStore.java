// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.EXPRESSION;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ExpressionStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ExpressionRecord;
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
 * Stores the texts in the Postgres table {@code expression}, with jOOQ. Each change of progress is
 * a conditional update.
 */
@Repository
class JooqExpressionStore implements ExpressionStore {
  private final DSLContext db;

  JooqExpressionStore(DSLContext db) {
    this.db = db;
  }

  /** How far a text is, as the column {@code expression.progress} holds it. */
  private enum Progress {
    QUEUED,
    BUILDING,
    BUILT,
    FAILED
  }

  @Override
  public Map<String, Text> texts(
      Collection<String> celexes, Language language, Map<String, TextId> stored) {
    return db.selectFrom(EXPRESSION)
        .where(EXPRESSION.CELEX.in(celexes), EXPRESSION.LANGUAGE_CODE.eq(language))
        .fetchMap(EXPRESSION.CELEX, row -> text(row, stored.get(row.getCelex())));
  }

  @Override
  public List<String> queue(Collection<String> celexes, Language language) {
    if (celexes.isEmpty()) {
      return List.of();
    }
    var insert =
        db.insertInto(
            EXPRESSION,
            EXPRESSION.CELEX,
            EXPRESSION.LANGUAGE_CODE,
            EXPRESSION.PROGRESS,
            EXPRESSION.ATTEMPT);
    for (var celex : celexes) {
      insert = insert.values(celex, language, Progress.QUEUED.name(), 0);
    }
    return insert.onConflictDoNothing().returning(EXPRESSION.CELEX).fetch(EXPRESSION.CELEX);
  }

  @Override
  public boolean start(String celex, Language language, int attempt) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.PROGRESS, Progress.BUILDING.name())
            .set(EXPRESSION.ATTEMPT, attempt)
            .where(the(celex, language), unsettled(), EXPRESSION.ATTEMPT.lt(attempt))
            .execute()
        == 1;
  }

  @Override
  public boolean defer(String celex, Language language, int attempt) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.PROGRESS, Progress.QUEUED.name())
            .set(EXPRESSION.ATTEMPT, attempt)
            .where(
                the(celex, language),
                unsettled(),
                EXPRESSION
                    .ATTEMPT
                    .lt(attempt)
                    .or(
                        EXPRESSION
                            .ATTEMPT
                            .eq(attempt)
                            .and(EXPRESSION.PROGRESS.eq(Progress.BUILDING.name()))))
            .execute()
        == 1;
  }

  @Override
  public boolean keep(String celex, Language language) {
    return db.insertInto(EXPRESSION)
            .set(EXPRESSION.CELEX, celex)
            .set(EXPRESSION.LANGUAGE_CODE, language)
            .set(EXPRESSION.PROGRESS, Progress.BUILT.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.BUILT_AT, currentInstant())
            .onConflict(EXPRESSION.CELEX, EXPRESSION.LANGUAGE_CODE)
            .doUpdate()
            .set(EXPRESSION.PROGRESS, Progress.BUILT.name())
            .set(EXPRESSION.BUILT_AT, currentInstant())
            .set(EXPRESSION.FAILURE, val(null, EXPRESSION.FAILURE))
            .set(EXPRESSION.TRACE, val(null, EXPRESSION.TRACE))
            .where(EXPRESSION.PROGRESS.ne(Progress.BUILT.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean requeue(String celex, Language language) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.PROGRESS, Progress.QUEUED.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.FAILURE, val(null, EXPRESSION.FAILURE))
            .set(EXPRESSION.TRACE, val(null, EXPRESSION.TRACE))
            .where(the(celex, language), EXPRESSION.PROGRESS.eq(Progress.FAILED.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean fail(String celex, Language language, Failure failure, @Nullable String trace) {
    return db.insertInto(EXPRESSION)
            .set(EXPRESSION.CELEX, celex)
            .set(EXPRESSION.LANGUAGE_CODE, language)
            .set(EXPRESSION.PROGRESS, Progress.FAILED.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.FAILURE, failure)
            .set(EXPRESSION.TRACE, trace)
            .onConflict(EXPRESSION.CELEX, EXPRESSION.LANGUAGE_CODE)
            .doUpdate()
            .set(EXPRESSION.PROGRESS, Progress.FAILED.name())
            .set(EXPRESSION.FAILURE, failure)
            .set(EXPRESSION.TRACE, trace)
            .where(unsettled())
            .execute()
        == 1;
  }

  private static Condition the(String celex, Language language) {
    return EXPRESSION.CELEX.eq(celex).and(EXPRESSION.LANGUAGE_CODE.eq(language));
  }

  /** Matches a text that is queued or building. Such a text is not built and has not failed. */
  private static Condition unsettled() {
    return EXPRESSION.PROGRESS.in(Progress.QUEUED.name(), Progress.BUILDING.name());
  }

  private static Text text(ExpressionRecord row, @Nullable TextId stored) {
    return switch (Progress.valueOf(row.getProgress())) {
      case QUEUED -> new Text.Waiting(row.getAttempt());
      case BUILDING -> new Text.Converting(row.getAttempt());
      case BUILT -> stored == null ? new Text.Waiting(0) : new Text.Ready(stored);
      case FAILED -> new Text.Failed(Objects.requireNonNull(row.getFailure()), row.getTrace());
    };
  }
}
