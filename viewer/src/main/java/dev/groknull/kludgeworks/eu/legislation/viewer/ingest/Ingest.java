// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.util.Optional;

/** Building expressions' legal texts, in the background. */
public interface Ingest {

  /**
   * Queues the expression to be built, unless it is queued, building or done already (built, or
   * failed: it isn't tried again by asking).
   *
   * @param celex the act's CELEX for its original text, a consolidation's for a consolidated
   *     version
   * @return how the expression's job stands
   */
  IngestStatus schedule(String celex, Language language);

  /** How the expression's job stands; none if it was never scheduled. */
  Optional<IngestStatus> status(String celex, Language language);
}
