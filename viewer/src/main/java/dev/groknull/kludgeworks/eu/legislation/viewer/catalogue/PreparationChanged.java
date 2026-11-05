// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.modulith.events.Externalized;

/**
 * One of an act's texts in a language stands otherwise: queued again, being built, built or failed.
 * Broadcast to every instance (stream preparation), for the pages waiting on the act, which read
 * how it stands anew (Texts.preparation).
 */
@DomainEvent
@Externalized("preparation")
public record PreparationChanged(ActIdentifier act, Language language) {}
