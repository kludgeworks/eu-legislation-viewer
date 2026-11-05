// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An act kept longer than viewer.catalogue.refresh-after was opened: it is to be read from Cellar
 * again (ActRefresher), once its claim on it is committed, so that one opening of it refreshes it.
 */
@DomainEvent
public record ActRefreshDue(ActIdentifier act) {}
