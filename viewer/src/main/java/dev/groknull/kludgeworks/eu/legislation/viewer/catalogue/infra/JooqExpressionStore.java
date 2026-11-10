// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.EXPRESSION;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ExpressionStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ExpressionRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Stores the texts in the Postgres table {@code expression}, with jOOQ. Each change of state is a
 * conditional update.
 */
@Repository
class JooqExpressionStore implements ExpressionStore {
  private final DSLContext db;

  JooqExpressionStore(DSLContext db) {
    this.db = db;
  }

  /** The state of a text, as the column {@code expression.state} holds it. */
  private enum State {
    QUEUED,
    BUILDING,
    BUILT,
    FAILED
  }

  @Override
  public Optional<ContentHash> root(String celex, Language language) {
    return db.select(EXPRESSION.ROOT)
        .from(EXPRESSION)
        .where(the(celex, language), EXPRESSION.ROOT.isNotNull())
        .fetchOptional(EXPRESSION.ROOT);
  }

  @Override
  public Map<String, ContentHash> roots(Collection<String> celexes, Language language) {
    return db.select(EXPRESSION.CELEX, EXPRESSION.ROOT)
        .from(EXPRESSION)
        .where(
            EXPRESSION.CELEX.in(celexes),
            EXPRESSION.LANGUAGE_CODE.eq(language),
            EXPRESSION.ROOT.isNotNull())
        .fetchMap(EXPRESSION.CELEX, EXPRESSION.ROOT);
  }

  @Override
  public Map<String, Text> texts(Collection<String> celexes, Language language) {
    return db.selectFrom(EXPRESSION)
        .where(EXPRESSION.CELEX.in(celexes), EXPRESSION.LANGUAGE_CODE.eq(language))
        .fetchMap(EXPRESSION.CELEX, JooqExpressionStore::text);
  }

  @Override
  public void queue(Collection<String> celexes, Language language) {
    if (celexes.isEmpty()) {
      return;
    }
    var insert =
        db.insertInto(
            EXPRESSION,
            EXPRESSION.CELEX,
            EXPRESSION.LANGUAGE_CODE,
            EXPRESSION.STATE,
            EXPRESSION.ATTEMPT);
    for (var celex : celexes) {
      insert = insert.values(celex, language, State.QUEUED.name(), 0);
    }
    insert.onConflictDoNothing().execute();
  }

  @Override
  public boolean start(String celex, Language language, int attempt) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.STATE, State.BUILDING.name())
            .set(EXPRESSION.ATTEMPT, attempt)
            .where(the(celex, language), unsettled(), EXPRESSION.ATTEMPT.lt(attempt))
            .execute()
        == 1;
  }

  @Override
  public boolean defer(String celex, Language language, int attempt) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.STATE, State.QUEUED.name())
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
                            .and(EXPRESSION.STATE.eq(State.BUILDING.name()))))
            .execute()
        == 1;
  }

  @Override
  public boolean keep(String celex, Language language, ContentHash root, String converter) {
    return db.insertInto(EXPRESSION)
            .set(EXPRESSION.CELEX, celex)
            .set(EXPRESSION.LANGUAGE_CODE, language)
            .set(EXPRESSION.STATE, State.BUILT.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.ROOT, root)
            .set(EXPRESSION.CONVERTER, converter)
            .set(EXPRESSION.BUILT_AT, currentInstant())
            .onConflict(EXPRESSION.CELEX, EXPRESSION.LANGUAGE_CODE)
            .doUpdate()
            .set(EXPRESSION.STATE, State.BUILT.name())
            .set(EXPRESSION.ROOT, root)
            .set(EXPRESSION.CONVERTER, converter)
            .set(EXPRESSION.BUILT_AT, currentInstant())
            .set(EXPRESSION.FAILURE, val(null, EXPRESSION.FAILURE))
            .set(EXPRESSION.TRACE, val(null, EXPRESSION.TRACE))
            .where(
                EXPRESSION
                    .STATE
                    .ne(State.BUILT.name())
                    .or(EXPRESSION.ROOT.ne(root))
                    .or(EXPRESSION.CONVERTER.ne(converter)))
            .execute()
        == 1;
  }

  @Override
  public boolean requeue(String celex, Language language) {
    return db.update(EXPRESSION)
            .set(EXPRESSION.STATE, State.QUEUED.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.FAILURE, val(null, EXPRESSION.FAILURE))
            .set(EXPRESSION.TRACE, val(null, EXPRESSION.TRACE))
            .where(the(celex, language), EXPRESSION.STATE.eq(State.FAILED.name()))
            .execute()
        == 1;
  }

  @Override
  public boolean fail(String celex, Language language, Failure failure, @Nullable String trace) {
    return db.insertInto(EXPRESSION)
            .set(EXPRESSION.CELEX, celex)
            .set(EXPRESSION.LANGUAGE_CODE, language)
            .set(EXPRESSION.STATE, State.FAILED.name())
            .set(EXPRESSION.ATTEMPT, 0)
            .set(EXPRESSION.FAILURE, failure)
            .set(EXPRESSION.TRACE, trace)
            .onConflict(EXPRESSION.CELEX, EXPRESSION.LANGUAGE_CODE)
            .doUpdate()
            .set(EXPRESSION.STATE, State.FAILED.name())
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
    return EXPRESSION.STATE.in(State.QUEUED.name(), State.BUILDING.name());
  }

  private static Text text(ExpressionRecord row) {
    return switch (State.valueOf(row.getState())) {
      case QUEUED -> new Text.Queued();
      case BUILDING -> new Text.Building(row.getAttempt());
      case BUILT -> new Text.Ready(Objects.requireNonNull(row.getRoot()));
      case FAILED -> new Text.Failed(Objects.requireNonNull(row.getFailure()), row.getTrace());
    };
  }
}
