// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Optional;

/**
 * Tells where the conversions of ingest stand. To ask for a text, publish {@link
 * ConversionRequested}: ingest converts in the background, and tells the outcome with its events.
 */
public interface Ingest {

  /** Returns the status of the job of the text. Returns none if the text was never asked for. */
  Optional<IngestStatus> status(String celex, Language language);

  /**
   * Returns about how many conversions are waiting or running ahead of the waiting conversions of
   * {@code celexes} in the language. It counts every other conversion queued before the first of
   * them. The number is approximate, because the workers take the jobs only roughly in that order.
   * Returns 0 if none of them is waiting.
   */
  int ahead(Collection<String> celexes, Language language);
}
