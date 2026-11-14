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
 * The ingest jobs, one for each expression, as they stand.
 *
 * <p>The event registry decides which job a worker runs next, with {@link ConversionQueued}. The
 * jobs decide only if a job can start, so that no two workers run one job. The times come from the
 * store, so one clock measures the times of all the instances.
 */
@Repository
public interface IngestJobs {

  /**
   * Adds a job for the expression, in the state queued, unless it has one.
   *
   * @return whether it was added
   */
  boolean add(String celex, Language language, @Nullable String traceparent);

  Optional<IngestJob> find(String celex, Language language);

  /**
   * Resets the job of the expression if it failed and another attempt may help ({@code
   * Failure.retryable}). A reset job is as a job just added: queued, at its first attempt, without
   * its outcome, and with the trace of the request that asked.
   *
   * @return whether it was reset
   */
  boolean resetRetryable(String celex, Language language, @Nullable String traceparent);

  /**
   * Resets the job of the expression if it is finished: built or failed. A reset job is as a job
   * just added: queued, at its first attempt, without its outcome, and with the trace of the
   * request that asked.
   *
   * @return whether it was reset
   */
  boolean resetFinished(String celex, Language language, @Nullable String traceparent);

  /**
   * Returns how many other jobs are queued or running that were queued before the first queued job
   * of {@code celexes}. Returns 0 if no job of {@code celexes} is queued.
   */
  int ahead(Collection<String> celexes, Language language);

  /**
   * Starts the job of the expression. It does this if the job is queued. It also does this if the
   * job is running but started longer ago than {@code takeover}. In that case, its worker stopped,
   * for example because its instance is gone. A start is an attempt, and changes the version of the
   * job.
   *
   * @return the started job. None if the job is running or done.
   */
  Optional<IngestJob> start(String celex, Language language, Duration takeover);

  /**
   * Writes the changes of the job. If the job is built or failed, it is finished now.
   *
   * @return the job as written, with its next version
   * @throws OptimisticLockingFailureException if it was started again since it was read
   */
  IngestJob save(IngestJob job);
}
