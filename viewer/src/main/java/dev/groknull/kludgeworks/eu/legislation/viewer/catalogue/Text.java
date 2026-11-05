// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import org.jmolecules.ddd.annotation.ValueObject;

/** Where a version's text in a language stands, for a reader asking for it. */
@ValueObject
public sealed interface Text {

  /** Whether it is built or failed: nothing more is done about it. */
  default boolean settled() {
    return switch (this) {
      case Ready _, Failed _ -> true;
      case Queued _, Building _ -> false;
    };
  }

  /** Built: its tree in the content module. */
  record Ready(ContentHash root) implements Text {}

  /** Waiting its turn to be built, behind other texts: first, or again after a failed attempt. */
  record Queued() implements Text {}

  /** Being built now. */
  record Building(int attempt) implements Text {}

  /** It couldn't be built, and won't be by asking again. */
  record Failed(Failure failure) implements Text {}
}
