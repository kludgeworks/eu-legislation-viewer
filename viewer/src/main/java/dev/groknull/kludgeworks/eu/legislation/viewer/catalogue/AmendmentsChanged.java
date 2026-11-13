// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * What other acts do to a held act changed in Cellar, for example because a new amending act or
 * corrigendum names it. The catalogue stored the act's amendments and dates anew. No text changes:
 * the lags and the pending amendments are worked out from the catalogue when they are read, so they
 * are correct at once.
 *
 * @param act the act
 */
@DomainEvent
public record AmendmentsChanged(ActIdentifier act) {}
