// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.ACT_CHANGE;
import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.CHANGE_SCAN;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ChangeStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.PendingChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.tables.records.ActChangeRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.types.DayToSecond;
import org.springframework.stereotype.Repository;

/**
 * Keeps the state of the scan of Cellar for changes in Postgres with jOOQ: the one row of {@code
 * change_scan}, and a row of {@code act_change} for each held act that the scan saw change. The
 * schema is in {@code db/migration/catalogue}.
 *
 * <p>A claim is one conditional update of {@code change_scan}, as an ingest job is taken over:
 * whichever instance updates the row first claims the scan, and the time of the claim is its token.
 */
@Repository
class JooqChangeStore implements ChangeStore {
  private final DSLContext db;

  JooqChangeStore(DSLContext db) {
    this.db = db;
  }

  @Override
  public Optional<Claim> claim(Duration every) {
    return db.update(CHANGE_SCAN)
        .set(CHANGE_SCAN.CLAIMED_AT, currentInstant())
        .where(CHANGE_SCAN.CLAIMED_AT.le(currentInstant().minus(DayToSecond.valueOf(every))))
        .returningResult(CHANGE_SCAN.WATERMARK, CHANGE_SCAN.CLAIMED_AT)
        .fetchOptional(row -> new Claim(row.value1(), row.value2()));
  }

  @Override
  public boolean advance(Claim claim, Instant watermark) {
    return db.update(CHANGE_SCAN)
            .set(
                CHANGE_SCAN.WATERMARK,
                greatest(CHANGE_SCAN.WATERMARK, val(watermark, CHANGE_SCAN.WATERMARK)))
            .where(CHANGE_SCAN.CLAIMED_AT.eq(claim.claimedAt()))
            .execute()
        == 1;
  }

  @Override
  public Map<ActIdentifier, PendingChange> pending(Collection<ActIdentifier> acts) {
    if (acts.isEmpty()) {
      return Map.of();
    }
    return db.selectFrom(ACT_CHANGE)
        .where(ACT_CHANGE.ACT.in(acts))
        .forUpdate()
        .fetchMap(ACT_CHANGE.ACT, JooqChangeStore::change);
  }

  @Override
  public void save(Collection<PendingChange> changes) {
    if (changes.isEmpty()) {
      return;
    }
    db.batch(
            changes.stream()
                .map(
                    change ->
                        db.insertInto(ACT_CHANGE)
                            .set(ACT_CHANGE.ACT, change.act())
                            .set(ACT_CHANGE.MODIFIED, change.modified())
                            .set(ACT_CHANGE.FIRST_SEEN, change.firstSeen())
                            .set(ACT_CHANGE.DUE_AT, change.due())
                            .set(ACT_CHANGE.READ_AT, change.readAt())
                            .onConflict(ACT_CHANGE.ACT)
                            .doUpdate()
                            .set(ACT_CHANGE.MODIFIED, change.modified())
                            .set(ACT_CHANGE.FIRST_SEEN, change.firstSeen())
                            .set(ACT_CHANGE.DUE_AT, change.due())
                            .set(ACT_CHANGE.READ_AT, change.readAt()))
                .toList())
        .execute();
  }

  @Override
  public List<PendingChange> due(Instant now) {
    return db.selectFrom(ACT_CHANGE)
        .where(ACT_CHANGE.DUE_AT.le(now))
        .orderBy(ACT_CHANGE.DUE_AT)
        .forUpdate()
        .skipLocked()
        .fetch(JooqChangeStore::change);
  }

  private static PendingChange change(ActChangeRecord row) {
    return new PendingChange(
        row.getAct(), row.getModified(), row.getFirstSeen(), row.getDueAt(), row.getReadAt());
  }
}
