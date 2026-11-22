// SPDX-License-Identifier: MIT
/**
 * What the comparison's fragments of the compare page show, worked out for their templates in
 * {@code src/main/jte/comparison}.
 *
 * <ul>
 *   <li>{@link DifferencesView} holds the differences in order, numbered and noted. The occurrences
 *       that are the same in both texts are folded. The opening of the occurrence that they are in
 *       is the context.
 *   <li>{@link SidenoteView} holds the sidenote of each difference: the acts that made it.
 *   <li>{@link OutlineView} holds the outline of the later text, with the differences counted on
 *       its branches.
 * </ul>
 *
 * <p>The page around these fragments is the catalogue's: its masthead, timeline, cards and
 * preparation. The fragments know it only by its address, the catalogue's {@code ComparePage}: they
 * link back to it, and no Java of the catalogue's web side is used here.
 *
 * <p>A view only formats. Each decision about the law or its dates, such as from when an act
 * applies, is a method of a domain class with its own test. A view reads the kind of a date or
 * whether it is for a fragment only to print it.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
