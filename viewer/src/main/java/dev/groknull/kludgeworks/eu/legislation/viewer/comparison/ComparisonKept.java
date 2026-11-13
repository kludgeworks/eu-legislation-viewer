// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A comparison was kept. One of its texts may have been replaced while it was worked out, after the
 * comparisons of that text were deleted. The module checks the texts when this arrives, and deletes
 * the comparison if one of them is gone.
 *
 * @param from the earlier text
 * @param to the later text
 */
@DomainEvent
public record ComparisonKept(TextId from, TextId to) {}
