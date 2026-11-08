// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * The texts of two neighbouring versions of an act are both built, in one language. The catalogue
 * publishes it when a text is built and the text of the version before it, or after it, is built
 * already. A reader will probably compare the two.
 *
 * @param earlier the root of the earlier version's text
 * @param later the root of the later version's text
 */
@DomainEvent
public record NeighboursBuilt(ContentHash earlier, ContentHash later) {}
