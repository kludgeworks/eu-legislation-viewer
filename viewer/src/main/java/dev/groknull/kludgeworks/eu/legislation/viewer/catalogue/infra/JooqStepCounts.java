// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.STEP_ACT_COUNT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.STEP_COUNT;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.StepCounts;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the step counts in the Postgres tables {@code step_count} and {@code step_act_count}, with
 * jOOQ.
 */
@Repository
class JooqStepCounts implements StepCounts {
  private final DSLContext db;

  JooqStepCounts(DSLContext db) {
    this.db = db;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Counted> find(ActIdentifier act, ContentHash from, ContentHash to) {
    return db.selectFrom(STEP_COUNT)
        .where(STEP_COUNT.ACT.eq(act), STEP_COUNT.FROM_ROOT.eq(from), STEP_COUNT.TO_ROOT.eq(to))
        .fetchOptional()
        .map(
            step ->
                new Counted(
                    db.selectFrom(STEP_ACT_COUNT)
                        .where(
                            STEP_ACT_COUNT.ACT.eq(act),
                            STEP_ACT_COUNT.FROM_ROOT.eq(from),
                            STEP_ACT_COUNT.TO_ROOT.eq(to))
                        .fetchMap(STEP_ACT_COUNT.AMENDING, STEP_ACT_COUNT.DIFFERENCES),
                    step.getTogether(),
                    step.getUnattributed()));
  }

  @Override
  @Transactional
  public void add(ActIdentifier act, ContentHash from, ContentHash to, Counted counted) {
    var added =
        db.insertInto(STEP_COUNT)
            .set(STEP_COUNT.ACT, act)
            .set(STEP_COUNT.FROM_ROOT, from)
            .set(STEP_COUNT.TO_ROOT, to)
            .set(STEP_COUNT.TOGETHER, counted.together())
            .set(STEP_COUNT.UNATTRIBUTED, counted.unknown())
            .set(STEP_COUNT.COUNTED_AT, currentInstant())
            .onConflictDoNothing()
            .execute();
    if (added == 0) {
      return;
    }
    db.batch(
            counted.differences().entrySet().stream()
                .map(
                    each ->
                        db.insertInto(STEP_ACT_COUNT)
                            .set(STEP_ACT_COUNT.ACT, act)
                            .set(STEP_ACT_COUNT.FROM_ROOT, from)
                            .set(STEP_ACT_COUNT.TO_ROOT, to)
                            .set(STEP_ACT_COUNT.AMENDING, each.getKey())
                            .set(STEP_ACT_COUNT.DIFFERENCES, each.getValue()))
                .toList())
        .execute();
  }
}
