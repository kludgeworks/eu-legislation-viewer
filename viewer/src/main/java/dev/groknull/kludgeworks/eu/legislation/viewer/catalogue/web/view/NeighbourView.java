// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;

/**
 * A unit the same in both texts, as a Synopsis row shows it (Design: Synopsis): the later text's in
 * both columns, with what last made it as it is.
 *
 * @param unit the unit as its row cites it
 * @param element the unit, as the later text has it
 * @param history what last made it as it is
 */
public record NeighbourView(Sibling unit, Element element, SidenoteView.History history) {}
