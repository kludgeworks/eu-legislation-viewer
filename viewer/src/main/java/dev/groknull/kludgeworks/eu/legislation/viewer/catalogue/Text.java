// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import org.jmolecules.ddd.annotation.ValueObject;

/** Where a version's text in a language stands, for a reader asking for it. */
@ValueObject
public sealed interface Text {

  /** Built: its tree in the content module. */
  record Ready(ContentHash root) implements Text {}

  /** Being built, in the background: what ingest is doing about it. */
  record Building(IngestStatus status) implements Text {}

  /** It couldn't be built, and won't be by asking again. */
  record Failed(Failure failure) implements Text {}

  /** The version has no text in the language that the viewer reads (no Formex). */
  record Missing() implements Text {}
}
