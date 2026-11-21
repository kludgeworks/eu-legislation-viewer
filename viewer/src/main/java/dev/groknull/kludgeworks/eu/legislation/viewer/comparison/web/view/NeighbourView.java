// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;

/**
 * An occurrence that is the same in both texts, as a row of the Synopsis shows it. Both columns
 * show the occurrence from the later text. The row also shows what last made the occurrence as it
 * is.
 *
 * @param occurrence the occurrence, as its row cites it
 * @param element the occurrence, as the later text has it
 * @param history what last made the occurrence as it is
 */
public record NeighbourView(Sibling occurrence, Element element, SidenoteView.History history) {}
