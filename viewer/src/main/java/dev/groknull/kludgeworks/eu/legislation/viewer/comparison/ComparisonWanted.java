// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A comparison a reader is likely to ask for, to work out ahead of them: the catalogue's, when a
 * version's text and that of the version next to it are both built. The module keeps it, as it
 * would one asked for (Comparisons).
 *
 * @param from the earlier text's root
 * @param to the later text's root
 */
@DomainEvent
public record ComparisonWanted(ContentHash from, ContentHash to) {}
