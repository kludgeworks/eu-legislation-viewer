// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
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
 * The work of building one expression: queued, claimed by a worker, which holds it while its
 * heartbeat is fresh, then built or failed. A job whose worker stops beating is claimed again; each
 * claim is an attempt, and a claim changes the version, so a worker that lost its job can't finish
 * it. Who holds it and when are the queue's (IngestJobs), not the job's. Its changes are its
 * withers' (RecordBuilder's IngestJobBuilder.With).
 *
 * @param traceparent the trace of the request that scheduled it (W3C Trace Context), which its work
 *     continues
 * @param root once built, the text's root, in hex
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
    @Nullable String root,
    @Nullable Failure failure,
    @Nullable String detail,
    long version)
    implements IngestJobBuilder.With {

  public IngestStatus status() {
    return new IngestStatus(
        state, stage, attempts, root == null ? null : new ContentHash(root), failure);
  }

  /** Converted, its text being stored. */
  public IngestJob storing() {
    return withStage(Stage.STORING);
  }

  public IngestJob built(ContentHash root) {
    return with(job -> job.state(State.BUILT).stage(null).root(root.hex()));
  }

  public IngestJob failed(Failure failure, String detail) {
    return with(job -> job.state(State.FAILED).stage(null).failure(failure).detail(detail));
  }

  /** Back in the queue, for another attempt. */
  public IngestJob retry() {
    return with(job -> job.state(State.QUEUED).stage(null));
  }
}
