// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * Cellar changed something about a held act, and the act has been quiet since ({@link ChangeScan}).
 * {@link ActRefresher} reads it from Cellar again. The event publication registry keeps the event
 * until the read succeeds, so a read that fails is tried again.
 */
@DomainEvent
public record ActChanged(ActIdentifier act) {}
