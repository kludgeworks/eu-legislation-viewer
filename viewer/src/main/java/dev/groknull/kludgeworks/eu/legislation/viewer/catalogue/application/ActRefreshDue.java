// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An act stored for longer than {@code viewer.catalogue.refresh-after} was opened. {@link
 * ActRefresher} reads it from Cellar again once the claim on it is committed. Thus only one opening
 * of the act refreshes it.
 */
@DomainEvent
public record ActRefreshDue(ActIdentifier act) {}
