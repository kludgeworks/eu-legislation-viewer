// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * An act's versions changed in Cellar: a new consolidated version, or one revised (an interim
 * version republished). Published once an act's change is seen (the ingestion feed's poller,
 * Design: Running in a cluster), for what is derived from its versions to follow: text converts
 * them ahead.
 *
 * @param act the act
 */
@DomainEvent
public record ActVersionsChanged(ActIdentifier act) {}
