// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
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
      case Waiting _, Converting _ -> false;
    };
  }

  /**
   * Built.
   *
   * @param text the text in the content module
   */
  record Ready(TextId text) implements Text {}

  /**
   * Waiting its turn to be converted, behind other texts. It waits for the first time, or again
   * after a failed attempt.
   *
   * @param attempt the last attempt that failed. It is 0 if none was made yet.
   */
  record Waiting(int attempt) implements Text {}

  /**
   * Being converted now.
   *
   * @param attempt which attempt this is, from 1
   */
  record Converting(int attempt) implements Text {}

  /**
   * The text could not be built. Another request does not try again.
   *
   * @param trace the trace id of the attempt that failed. It is null if no trace was recorded.
   */
  record Failed(Failure failure, @Nullable String trace) implements Text {}
}
