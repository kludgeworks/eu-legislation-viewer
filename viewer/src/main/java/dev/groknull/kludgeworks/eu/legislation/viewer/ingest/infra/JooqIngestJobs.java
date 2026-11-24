// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.jooq.Tables.INGEST_JOB;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.val;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestProgress.Progress;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra.jooq.tables.records.IngestJobRecord;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.types.DayToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** The ingest jobs in Postgres, in the table {@code ingest_job}, with jOOQ. */
@Repository
class JooqIngestJobs implements IngestJobs {
  private final DSLContext db;

  JooqIngestJobs(DSLContext db) {
    this.db = db;
  }

  @Override
  public boolean add(String celex, Language language, @Nullable String traceparent) {
    return db.insertInto(INGEST_JOB)
            .set(INGEST_JOB.CELEX, celex)
            .set(INGEST_JOB.LANGUAGE_CODE, language)
            .set(INGEST_JOB.PROGRESS, Progress.QUEUED)
            .set(INGEST_JOB.ATTEMPTS, 0)
            .set(INGEST_JOB.TRACEPARENT, traceparent)
            .set(INGEST_JOB.CREATED_AT, currentInstant())
            .set(INGEST_JOB.REVISION, 0L)
            .onConflict(INGEST_JOB.CELEX, INGEST_JOB.LANGUAGE_CODE)
            .doNothing()
            .execute()
        == 1;
  }

  @Override
  public Optional<IngestJob> find(String celex, Language language) {
    return db.selectFrom(INGEST_JOB)
        .where(INGEST_JOB.CELEX.eq(celex), INGEST_JOB.LANGUAGE_CODE.eq(language))
        .fetchOptional()
        .map(JooqIngestJobs::job);
  }

  @Override
  public boolean resetRetryable(String celex, Language language, @Nullable String traceparent) {
    var retryable = Arrays.stream(Failure.values()).filter(Failure::retryable).toList();
    return reset(
        celex,
        language,
        traceparent,
        INGEST_JOB.PROGRESS.eq(Progress.FAILED).and(INGEST_JOB.FAILURE.in(retryable)));
  }

  @Override
  public boolean resetFinished(String celex, Language language, @Nullable String traceparent) {
    return reset(
        celex, language, traceparent, INGEST_JOB.PROGRESS.in(Progress.BUILT, Progress.FAILED));
  }

  /** Resets the job of the text, to a job just added, if it is as {@code when}. */
  private boolean reset(
      String celex, Language language, @Nullable String traceparent, Condition when) {
    return db.update(INGEST_JOB)
            .set(INGEST_JOB.PROGRESS, Progress.QUEUED)
            .set(INGEST_JOB.ATTEMPTS, 0)
            .set(INGEST_JOB.TRACEPARENT, traceparent)
            .set(INGEST_JOB.FAILURE, val(null, INGEST_JOB.FAILURE))
            .set(INGEST_JOB.DETAIL, val(null, INGEST_JOB.DETAIL))
            .set(INGEST_JOB.FINISHED_AT, val(null, INGEST_JOB.FINISHED_AT))
            .set(INGEST_JOB.REVISION, INGEST_JOB.REVISION.plus(1))
            .where(INGEST_JOB.CELEX.eq(celex), INGEST_JOB.LANGUAGE_CODE.eq(language), when)
            .execute()
        == 1;
  }

  @Override
  public int ahead(Collection<String> celexes, Language language) {
    var first =
        db.select(min(INGEST_JOB.CREATED_AT))
            .from(INGEST_JOB)
            .where(
                INGEST_JOB.CELEX.in(celexes),
                INGEST_JOB.LANGUAGE_CODE.eq(language),
                INGEST_JOB.PROGRESS.eq(Progress.QUEUED));
    return db.fetchCount(
        INGEST_JOB,
        INGEST_JOB.PROGRESS.in(Progress.QUEUED, Progress.RUNNING),
        INGEST_JOB.CREATED_AT.lt(first),
        not(INGEST_JOB.CELEX.in(celexes).and(INGEST_JOB.LANGUAGE_CODE.eq(language))));
  }

  @Override
  public Optional<IngestJob> start(String celex, Language language, Duration takeover) {
    return db.update(INGEST_JOB)
        .set(INGEST_JOB.PROGRESS, Progress.RUNNING)
        .set(INGEST_JOB.STARTED_AT, currentInstant())
        .set(INGEST_JOB.ATTEMPTS, INGEST_JOB.ATTEMPTS.plus(1))
        .set(INGEST_JOB.REVISION, INGEST_JOB.REVISION.plus(1))
        .where(
            INGEST_JOB.CELEX.eq(celex),
            INGEST_JOB.LANGUAGE_CODE.eq(language),
            INGEST_JOB
                .PROGRESS
                .eq(Progress.QUEUED)
                .or(
                    INGEST_JOB
                        .PROGRESS
                        .eq(Progress.RUNNING)
                        .and(
                            INGEST_JOB.STARTED_AT.lt(
                                currentInstant().minus(DayToSecond.valueOf(takeover))))))
        .returning()
        .fetchOptional()
        .map(JooqIngestJobs::job);
  }

  @Override
  public IngestJob save(IngestJob job) {
    var finished = job.progress() == Progress.BUILT || job.progress() == Progress.FAILED;
    return db.update(INGEST_JOB)
        .set(INGEST_JOB.PROGRESS, job.progress())
        .set(INGEST_JOB.FAILURE, job.failure())
        .set(INGEST_JOB.DETAIL, job.detail())
        .set(
            INGEST_JOB.FINISHED_AT, finished ? currentInstant() : val(null, INGEST_JOB.FINISHED_AT))
        .set(INGEST_JOB.REVISION, INGEST_JOB.REVISION.plus(1))
        .where(INGEST_JOB.ID.eq(job.id()), INGEST_JOB.REVISION.eq(job.version()))
        .returning()
        .fetchOptional()
        .map(JooqIngestJobs::job)
        .orElseThrow(() ->
            new OptimisticLockingFailureException(
                "Ingest job " + job.id() + " was started again since version " + job.version()));
  }

  private static IngestJob job(IngestJobRecord row) {
    return new IngestJob(
        row.getId(),
        row.getCelex(),
        row.getLanguageCode(),
        row.getProgress(),
        row.getAttempts(),
        row.getTraceparent(),
        row.getFailure(),
        row.getDetail(),
        row.getRevision());
  }
}
