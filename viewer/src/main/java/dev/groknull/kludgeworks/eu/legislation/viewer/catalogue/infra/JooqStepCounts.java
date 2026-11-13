// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.TEXT_STEP_ACT_COUNT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.TEXT_STEP_COUNT;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.StepCounts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the step counts in the Postgres tables {@code text_step_count} and {@code
 * text_step_act_count}, with jOOQ.
 */
@Repository
class JooqStepCounts implements StepCounts {
  private final DSLContext db;

  JooqStepCounts(DSLContext db) {
    this.db = db;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Counted> find(ActIdentifier act, TextId from, TextId to) {
    return db.selectFrom(TEXT_STEP_COUNT)
        .where(
            TEXT_STEP_COUNT.ACT.eq(act),
            TEXT_STEP_COUNT.FROM_TEXT.eq(from),
            TEXT_STEP_COUNT.TO_TEXT.eq(to))
        .fetchOptional()
        .map(
            step ->
                new Counted(
                    db.selectFrom(TEXT_STEP_ACT_COUNT)
                        .where(
                            TEXT_STEP_ACT_COUNT.ACT.eq(act),
                            TEXT_STEP_ACT_COUNT.FROM_TEXT.eq(from),
                            TEXT_STEP_ACT_COUNT.TO_TEXT.eq(to))
                        .fetchMap(TEXT_STEP_ACT_COUNT.AMENDING, TEXT_STEP_ACT_COUNT.DIFFERENCES),
                    step.getTogether(),
                    step.getUnattributed()));
  }

  @Override
  @Transactional
  public void add(ActIdentifier act, TextId from, TextId to, Counted counted) {
    var added =
        db.insertInto(TEXT_STEP_COUNT)
            .set(TEXT_STEP_COUNT.ACT, act)
            .set(TEXT_STEP_COUNT.FROM_TEXT, from)
            .set(TEXT_STEP_COUNT.TO_TEXT, to)
            .set(TEXT_STEP_COUNT.TOGETHER, counted.together())
            .set(TEXT_STEP_COUNT.UNATTRIBUTED, counted.unknown())
            .set(TEXT_STEP_COUNT.COUNTED_AT, currentInstant())
            .onConflictDoNothing()
            .execute();
    if (added == 0) {
      return;
    }
    db.batch(
            counted.differences().entrySet().stream()
                .map(
                    each ->
                        db.insertInto(TEXT_STEP_ACT_COUNT)
                            .set(TEXT_STEP_ACT_COUNT.ACT, act)
                            .set(TEXT_STEP_ACT_COUNT.FROM_TEXT, from)
                            .set(TEXT_STEP_ACT_COUNT.TO_TEXT, to)
                            .set(TEXT_STEP_ACT_COUNT.AMENDING, each.getKey())
                            .set(TEXT_STEP_ACT_COUNT.DIFFERENCES, each.getValue()))
                .toList())
        .execute();
  }

  @Override
  @Transactional
  public int remove(TextId text) {
    return db.deleteFrom(TEXT_STEP_COUNT)
        .where(TEXT_STEP_COUNT.FROM_TEXT.eq(text).or(TEXT_STEP_COUNT.TO_TEXT.eq(text)))
        .execute();
  }
}
