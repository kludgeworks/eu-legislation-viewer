// SPDX-License-Identifier: MIT
/**
 * The web side of the comparison module: the fragments of the compare page that come from the
 * comparison, {@link DifferencesController}. The page itself is the catalogue's. It has slots that
 * htmx fills from this module's addresses: the changes, in one of three views, and the acts between
 * the two versions, with how many differences each made. The fragments use the typed facade of
 * their templates in {@code src/main/jte/comparison}.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
