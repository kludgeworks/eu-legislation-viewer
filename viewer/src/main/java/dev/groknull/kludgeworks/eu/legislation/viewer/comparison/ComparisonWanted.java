// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A comparison that a reader will probably ask for, to be worked out before the reader asks. The
 * catalogue publishes it when the text of a version and the text of the next version are both
 * built. The module keeps the comparison, as it keeps one that {@link Comparisons} was asked for.
 *
 * @param from the root of the earlier text
 * @param to the root of the later text
 */
@DomainEvent
public record ComparisonWanted(ContentHash from, ContentHash to) {}
