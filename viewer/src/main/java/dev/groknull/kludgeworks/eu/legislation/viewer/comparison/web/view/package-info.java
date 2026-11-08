// SPDX-License-Identifier: MIT
/**
 * What the comparison's parts of the compare page show, worked out for their templates in {@code
 * src/main/jte/comparison}.
 *
 * <ul>
 *   <li>{@link ActsView} holds the acts between the two versions as chips, with their counts.
 *   <li>{@link ChangesView} holds the differences in order, numbered and noted. The units that are
 *       the same in both texts are folded. The opening of the unit that they are in is the context.
 *   <li>{@link SidenoteView} holds the note beside each difference: the acts that made it.
 *   <li>{@link OutlineView} holds the outline of the later text, with the differences counted on
 *       its branches.
 * </ul>
 *
 * <p>The page around these parts is the catalogue's: its masthead, timeline, cards and preparation.
 * The parts know it only by its address, the {@code comparepage} library's {@code ComparePage}:
 * they link back to it, and no Java of the catalogue's web side is used here.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
