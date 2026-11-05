// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import org.jspecify.annotations.Nullable;

/**
 * How an expression's job stands: what a reader waiting for its text is told.
 *
 * @param stage while running, what it is doing
 * @param attempts how often it was started
 * @param root once built, the text's root
 * @param converter once built, the version of the converter that made it
 * @param failure once failed, why
 */
public record IngestStatus(
    State state,
    @Nullable Stage stage,
    int attempts,
    @Nullable ContentHash root,
    @Nullable String converter,
    @Nullable Failure failure) {

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
