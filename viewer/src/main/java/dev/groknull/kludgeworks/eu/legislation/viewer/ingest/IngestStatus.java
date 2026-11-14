// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jspecify.annotations.Nullable;

/**
 * The status of the job of an expression. A reader who waits for its text sees this status.
 *
 * @param attempts how often it was started
 * @param failure once failed, why
 */
public record IngestStatus(State state, int attempts, @Nullable Failure failure) {

  public enum State {
    QUEUED,
    RUNNING,
    BUILT,
    FAILED
  }
}
