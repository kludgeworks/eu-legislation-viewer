// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The ingest jobs: each expression's, as it stands. Which job a worker runs next is the event
 * registry's (ExpressionRequested), not theirs; starting one is theirs, so no two workers run one
 * job. Its times are the store's, so the instances' are measured by one clock.
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
   * How many other jobs are queued or running that were queued before the first of those of {@code
   * celexes} that is queued; none if none is.
   */
  int ahead(Collection<String> celexes, Language language);

  /**
   * Starts the expression's job, if it is queued; or running, but started longer ago than {@code
   * takeover} (its worker stopped, on an instance gone). A start is an attempt, and changes the
   * job's version.
   *
   * @return the job started; none if it is running or done
   */
  Optional<IngestJob> start(String celex, Language language, Duration takeover);

  /**
   * Writes the job's changes. One built or failed is finished now.
   *
   * @return the job as written, with its next version
   * @throws OptimisticLockingFailureException if it was started again since it was read
   */
  IngestJob save(IngestJob job);
}
