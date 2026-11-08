// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;

/**
 * A unit that is the same in both texts, as a row of the Synopsis shows it. Both columns show the
 * unit from the later text. The row also shows what last made the unit as it is.
 *
 * @param unit the unit, as its row cites it
 * @param element the unit, as the later text has it
 * @param history what last made the unit as it is
 */
public record NeighbourView(Sibling unit, Element element, SidenoteView.History history) {}
