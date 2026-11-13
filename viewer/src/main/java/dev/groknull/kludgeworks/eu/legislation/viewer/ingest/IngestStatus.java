// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jspecify.annotations.Nullable;

/**
 * The status of the job of an expression. A reader who waits for its text sees this status.
 *
 * @param stage while running, what it is doing
 * @param attempts how often it was started
 * @param failure once failed, why
 */
public record IngestStatus(
    State state, @Nullable Stage stage, int attempts, @Nullable Failure failure) {

  public enum State {
    QUEUED,
    RUNNING,
    BUILT,
    FAILED
  }

  public enum Stage {
    /** Fetching the Formex from Cellar and converting it. */
    CONVERTING,
    /** Storing the legal text. */
    STORING
  }
}
