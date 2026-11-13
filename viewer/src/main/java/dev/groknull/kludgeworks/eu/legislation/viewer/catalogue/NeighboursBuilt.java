// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * The texts of two neighbouring versions of an act are both built, in one language. The catalogue
 * publishes it when a text is built and the text of the version before it, or after it, is built
 * already. A reader will probably compare the two.
 *
 * @param earlier the earlier version's text
 * @param later the later version's text
 */
@DomainEvent
public record NeighboursBuilt(TextId earlier, TextId later) {}
