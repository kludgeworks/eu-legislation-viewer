// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.Stage;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * The work to build one expression. A job is queued, then a worker starts it. Then it is built,
 * failed, or back in the queue for another attempt.
 *
 * <p>Each start is an attempt and changes the version. Another worker can take over the job with
 * {@link IngestJobs#start}. The first worker then cannot finish it. The withers of {@code
 * IngestJobBuilder.With}, which RecordBuilder generates, make the changes.
 *
 * @param traceparent the trace of the request that scheduled the job, in W3C Trace Context form.
 *     The work continues this trace.
 */
@AggregateRoot
@RecordBuilder
public record IngestJob(
    @Identity long id,
    String celex,
    Language language,
    State state,
    @Nullable Stage stage,
    int attempts,
    @Nullable String traceparent,
    @Nullable Failure failure,
    @Nullable String detail,
    long version)
    implements IngestJobBuilder.With {

  public IngestStatus status() {
    return new IngestStatus(state, stage, attempts, failure);
  }

  /** Returns the job converted, with its text being stored. */
  public IngestJob storing() {
    return withStage(Stage.STORING);
  }

  public IngestJob built() {
    return with(job -> job.state(State.BUILT).stage(null));
  }

  public IngestJob failed(Failure failure, String detail) {
    return with(job -> job.state(State.FAILED).stage(null).failure(failure).detail(detail));
  }

  /** Returns the job back in the queue, for another attempt. */
  public IngestJob retry() {
    return with(job -> job.state(State.QUEUED).stage(null));
  }
}
