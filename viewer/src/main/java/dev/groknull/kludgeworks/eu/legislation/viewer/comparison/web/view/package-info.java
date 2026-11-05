// SPDX-License-Identifier: MIT
/**
 * What the comparison's Changes view shows, worked out for its template (ChangesView): the
 * differences in order, numbered and noted, with the units the same in both folded and the opening
 * of the unit they are in as context; and the later text's outline, its branches counted
 * (OutlineView). Its own named interface (view), for the catalogue's comparison page, which shows
 * it with the acts that made each difference.
 */
@InfrastructureRing
@NamedInterface("view")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
