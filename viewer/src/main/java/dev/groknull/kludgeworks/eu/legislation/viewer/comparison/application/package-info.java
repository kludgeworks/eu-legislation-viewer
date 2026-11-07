// SPDX-License-Identifier: MIT
/**
 * The application ring of the comparison module. {@link ComparisonService} compares two stored
 * texts. Each comparison is a unit of work, and is logged.
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
