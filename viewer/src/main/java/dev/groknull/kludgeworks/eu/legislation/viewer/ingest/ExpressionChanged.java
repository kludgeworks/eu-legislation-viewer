// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.modulith.events.Externalized;

/**
 * An expression's job changed: queued, claimed by a worker, storing, built, failed or back in the
 * queue. Externalized to the stream ingest, so every instance hears it once the change is committed
 * (the broadcast module's Broadcast of it), for pages waiting on it; not kept, so a listener that
 * missed one only misses a moment, and reads the job's status anew (Ingest.status).
 */
@DomainEvent
@Externalized("ingest")
public record ExpressionChanged(String celex, Language language) {}
