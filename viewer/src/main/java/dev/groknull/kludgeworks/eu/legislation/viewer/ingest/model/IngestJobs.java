// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.Duration;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The ingest jobs: the queue every instance's workers claim from. Its times are the store's, so the
 * instances' leases are measured by one clock.
 */
@Repository
public interface IngestJobs {

  /**
   * Queues a job for the expression unless it has one.
   *
   * @return whether it was queued
   */
  boolean schedule(String celex, Language language, @Nullable String traceparent);

  Optional<IngestJob> find(String celex, Language language);

  /**
   * Claims the oldest job queued, or running on a worker whose heartbeat is older than the lease:
   * locked while claimed, so no two workers claim one job, and those another worker has locked are
   * skipped, not waited on. A claim is an attempt, and changes the job's version.
   */
  Optional<IngestJob> claim(String owner, Duration lease);

  /**
   * Keeps the jobs a worker holds.
   *
   * @return how many it holds
   */
  int heartbeat(String owner);

  /**
   * Writes the job's changes. One no longer running is no worker's; one built or failed is finished
   * now.
   *
   * @return the job as written, with its next version
   * @throws OptimisticLockingFailureException if it was claimed again since it was read
   */
  IngestJob save(IngestJob job);
}
