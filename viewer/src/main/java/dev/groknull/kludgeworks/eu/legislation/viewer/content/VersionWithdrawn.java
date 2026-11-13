// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * A version no longer exists: Cellar withdrew it, and the module that keeps the versions, the
 * catalogue, deleted it. The content module deletes the texts of the version, in every language,
 * and publishes {@link TextRemoved} for each.
 *
 * <p>The event is in this module, though the catalogue publishes it, because the catalogue depends
 * on this module and not the other way.
 *
 * @param celex the document that held the version's text, for example {@code 02015R1998-20260322}
 */
@DomainEvent
public record VersionWithdrawn(String celex) {}
