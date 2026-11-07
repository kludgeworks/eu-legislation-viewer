// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/** Where a version's text in a language stands, for a reader asking for it. */
@ValueObject
public sealed interface Text {

  /** Returns whether it is built or failed. Nothing more is done about it then. */
  default boolean settled() {
    return switch (this) {
      case Ready _, Failed _ -> true;
      case Queued _, Building _ -> false;
    };
  }

  /** Built. Its tree is in the content module. */
  record Ready(ContentHash root) implements Text {}

  /**
   * Waiting its turn to be built, behind other texts. It waits for the first time, or again after a
   * failed attempt.
   */
  record Queued() implements Text {}

  /** Being built now. */
  record Building(int attempt) implements Text {}

  /**
   * The text could not be built. Another request does not try again.
   *
   * @param trace the trace id of the attempt that failed. It is null if no trace was recorded.
   */
  record Failed(Failure failure, @Nullable String trace) implements Text {}
}
