// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.PREPARATION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.PREPARATION_STEP;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation.Step;
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

/** The preparations in Postgres (preparation, preparation_step), with jOOQ. */
@Repository
class JooqPreparationStore implements PreparationStore {
  private final DSLContext db;

  JooqPreparationStore(DSLContext db) {
    this.db = db;
  }

  /** A preparation's state, as preparation.state has it. */
  private enum State {
    PREPARING,
    PREPARED,
    FAILED
  }

  @Override
  public void start(ActIdentifier act, Language language) {
    db.insertInto(PREPARATION)
        .set(PREPARATION.ACT, act)
        .set(PREPARATION.LANGUAGE, language)
        .set(PREPARATION.STATE, State.PREPARING.name())
        .set(PREPARATION.STARTED_AT, currentInstant())
        .onConflictDoNothing()
        .execute();
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
        // its key never changes: no conflict with a step's foreign key on it
        .forNoKeyUpdate()
        .fetchOptional()
        .map(JooqPreparationStore::kept);
  }

  @Override
  public boolean settle(ActIdentifier act, Language language, boolean prepared) {
    return db.update(PREPARATION)
            .set(PREPARATION.STATE, (prepared ? State.PREPARED : State.FAILED).name())
            .set(PREPARATION.SETTLED_AT, currentInstant())
            .where(the(act, language), PREPARATION.STATE.eq(State.PREPARING.name()))
            .execute()
        == 1;
  }

  @Override
  public void step(
      ActIdentifier act,
      Language language,
      String celex,
      Step.Kind kind,
      @Nullable Integer attempt,
      @Nullable Failure failure) {
    db.insertInto(
            PREPARATION_STEP,
            PREPARATION_STEP.ACT,
            PREPARATION_STEP.LANGUAGE,
            PREPARATION_STEP.AT,
            PREPARATION_STEP.CELEX,
            PREPARATION_STEP.KIND,
            PREPARATION_STEP.ATTEMPT,
            PREPARATION_STEP.FAILURE)
        .select(
            select(
                    val(act, PREPARATION_STEP.ACT),
                    val(language, PREPARATION_STEP.LANGUAGE),
                    currentInstant(),
                    val(celex),
                    val(kind.name()),
                    val(attempt, PREPARATION_STEP.ATTEMPT),
                    val(failure, PREPARATION_STEP.FAILURE))
                .whereExists(selectOne().from(PREPARATION).where(the(act, language))))
        .execute();
  }

  @Override
  public List<Step> steps(ActIdentifier act, Language language) {
    var rows =
        db.selectFrom(PREPARATION_STEP)
            .where(PREPARATION_STEP.ACT.eq(act), PREPARATION_STEP.LANGUAGE.eq(language))
            .orderBy(PREPARATION_STEP.AT)
            .fetch();
    // each text's last start: its attempt, and when
    var starts = new HashMap<String, Step>();
    var steps = new ArrayList<Step>(rows.size());
    for (var row : rows) {
      var kind = Step.Kind.valueOf(row.getKind());
      var at = row.getAt();
      var celex = row.getCelex();
      var start = starts.get(celex);
      var step =
          switch (kind) {
            case STARTED, DEFERRED ->
                new Step(at, celex, kind, attempt(row.getAttempt()), null, null);
            case BUILT, FAILED ->
                new Step(
                    at,
                    celex,
                    kind,
                    start == null ? 0 : start.attempt(),
                    start == null ? null : Duration.between(start.at(), at),
                    row.getFailure());
          };
      if (kind == Step.Kind.STARTED) {
        starts.put(celex, step);
      }
      steps.add(step);
    }
    return steps;
  }

  private static int attempt(@Nullable Integer attempt) {
    return attempt == null ? 0 : attempt;
  }

  private static Condition the(ActIdentifier act, Language language) {
    return PREPARATION.ACT.eq(act).and(PREPARATION.LANGUAGE.eq(language));
  }

  private static Kept kept(PreparationRecord row) {
    return new Kept(row.getStartedAt(), row.getSettledAt());
  }
}
