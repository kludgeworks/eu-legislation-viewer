// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Optional;

/** Builds the legal texts of expressions in the background. */
public interface Ingest {

  /**
   * Queues the expression to be built, unless it is queued, building or done already. Done means
   * built or failed. A failed expression is not tried again when this method is called again.
   *
   * @param celex the CELEX of the act for its original text, or the CELEX of a consolidation for a
   *     consolidated version
   * @return the status of the job of the expression
   */
  IngestStatus schedule(String celex, Language language);

  /**
   * Queues the job of the expression again, from its first attempt. It does this only if the job
   * failed and another attempt may help ({@link Failure#retryable}).
   *
   * @return whether it was queued again
   */
  boolean retry(String celex, Language language);

  /**
   * Builds the expression again, because its source changed: Cellar republished its Formex. It
   * queues the job again, from its first attempt, if it is built or failed. A job that is queued or
   * building is left. An expression without a job is queued.
   *
   * @return the status of the job of the expression
   */
  IngestStatus rebuild(String celex, Language language);

  /** Returns the status of the job of the expression. Returns none if it was never scheduled. */
  Optional<IngestStatus> status(String celex, Language language);

  /**
   * Returns about how many expressions are queued or building ahead of the queued expressions of
   * {@code celexes} in the language. It counts every other expression queued before the first of
   * them. The number is approximate, because the workers take the jobs only roughly in that order.
   * Returns 0 if none of them is queued.
   */
  int ahead(Collection<String> celexes, Language language);
}
