// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.jooq.Tables.INGEST_JOB;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.Stage;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.jooq.tables.records.IngestJobRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.types.DayToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** The ingest jobs in Postgres (ingest_job), with jOOQ. */
@Repository
class JooqIngestJobs implements IngestJobs {
  private final DSLContext db;

  JooqIngestJobs(DSLContext db) {
    this.db = db;
  }

  @Override
  public boolean schedule(String celex, Language language, @Nullable String traceparent) {
    return db.insertInto(INGEST_JOB)
            .set(INGEST_JOB.CELEX, celex)
            .set(INGEST_JOB.LANGUAGE, language)
            .set(INGEST_JOB.STATE, State.QUEUED)
            .set(INGEST_JOB.ATTEMPTS, 0)
            .set(INGEST_JOB.TRACEPARENT, traceparent)
            .set(INGEST_JOB.CREATED_AT, currentInstant())
            .set(INGEST_JOB.VERSION, 0L)
            .onConflict(INGEST_JOB.CELEX, INGEST_JOB.LANGUAGE)
            .doNothing()
            .execute()
        == 1;
  }

  @Override
  public Optional<IngestJob> find(String celex, Language language) {
    return db.selectFrom(INGEST_JOB)
        .where(INGEST_JOB.CELEX.eq(celex), INGEST_JOB.LANGUAGE.eq(language))
        .fetchOptional()
        .map(JooqIngestJobs::job);
  }

  @Override
  public Optional<IngestJob> start(String celex, Language language, Duration takeover) {
    return db.update(INGEST_JOB)
        .set(INGEST_JOB.STATE, State.RUNNING)
        .set(INGEST_JOB.STAGE, Stage.CONVERTING)
        .set(INGEST_JOB.STARTED_AT, currentInstant())
        .set(INGEST_JOB.ATTEMPTS, INGEST_JOB.ATTEMPTS.plus(1))
        .set(INGEST_JOB.VERSION, INGEST_JOB.VERSION.plus(1))
        .where(
            INGEST_JOB.CELEX.eq(celex),
            INGEST_JOB.LANGUAGE.eq(language),
            INGEST_JOB
                .STATE
                .eq(State.QUEUED)
                .or(
                    INGEST_JOB
                        .STATE
                        .eq(State.RUNNING)
                        .and(
                            INGEST_JOB.STARTED_AT.lt(
                                currentInstant().minus(DayToSecond.valueOf(takeover))))))
        .returning()
        .fetchOptional()
        .map(JooqIngestJobs::job);
  }

  @Override
  public IngestJob save(IngestJob job) {
    var finished = job.state() == State.BUILT || job.state() == State.FAILED;
    return db.update(INGEST_JOB)
        .set(INGEST_JOB.STATE, job.state())
        .set(INGEST_JOB.STAGE, job.stage())
        .set(INGEST_JOB.ROOT, job.root())
        .set(INGEST_JOB.CONVERTER, job.converter())
        .set(INGEST_JOB.FAILURE, job.failure())
        .set(INGEST_JOB.DETAIL, job.detail())
        .set(
            INGEST_JOB.FINISHED_AT, finished ? currentInstant() : val(null, INGEST_JOB.FINISHED_AT))
        .set(INGEST_JOB.VERSION, INGEST_JOB.VERSION.plus(1))
        .where(INGEST_JOB.ID.eq(job.id()), INGEST_JOB.VERSION.eq(job.version()))
        .returning()
        .fetchOptional()
        .map(JooqIngestJobs::job)
        .orElseThrow(
            () ->
                new OptimisticLockingFailureException(
                    "Ingest job "
                        + job.id()
                        + " was started again since version "
                        + job.version()));
  }

  private static IngestJob job(IngestJobRecord row) {
    return new IngestJob(
        row.getId(),
        row.getCelex(),
        row.getLanguage(),
        row.getState(),
        row.getStage(),
        row.getAttempts(),
        row.getTraceparent(),
        row.getRoot(),
        row.getConverter(),
        row.getFailure(),
        row.getDetail(),
        row.getVersion());
  }
}
