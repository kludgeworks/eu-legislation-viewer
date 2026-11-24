// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jspecify.annotations.Nullable;

/**
 * The progress of the job of a text. A reader who waits for the text sees this progress.
 *
 * @param attempts how often it was started
 * @param failure once failed, why
 */
public record IngestProgress(Progress progress, int attempts, @Nullable Failure failure) {

  /** How far the job is: queued, running, built, or failed. */
  public enum Progress {
    QUEUED,
    RUNNING,
    BUILT,
    FAILED
  }
}
