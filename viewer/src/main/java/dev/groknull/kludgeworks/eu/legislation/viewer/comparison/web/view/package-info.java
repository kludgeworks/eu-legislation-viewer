// SPDX-License-Identifier: MIT
/**
 * What the Changes view of a comparison shows, worked out for its template.
 *
 * <ul>
 *   <li>{@link ChangesView} holds the differences in order, numbered and noted. The units that are
 *       the same in both texts are folded. The opening of the unit that they are in is the context.
 *   <li>{@link OutlineView} holds the outline of the later text, with the differences counted on
 *       its branches.
 * </ul>
 *
 * <p>It is a named interface of its own ({@code view}), for the comparison page of the catalogue.
 * That page shows it with the acts that made each difference.
 */
@InfrastructureRing
@NamedInterface("view")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
