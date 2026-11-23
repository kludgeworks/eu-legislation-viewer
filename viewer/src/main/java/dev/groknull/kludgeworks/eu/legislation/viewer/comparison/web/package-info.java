// SPDX-License-Identifier: MIT
/**
 * The web side of the comparison module: the fragments of the compare page and of the page of an
 * amending act that come from the comparison. The pages themselves are the catalogue's. They have
 * slots that htmx fills from this module's addresses:
 *
 * <ul>
 *   <li>{@link ComparisonViewsController}: the differences, in one of three views, and the folds of
 *       the Synopsis
 *   <li>{@link DifferenceController}: one difference, the differences at a place, and the
 *       differences that an act or another act made
 * </ul>
 *
 * <p>{@link Fragments} has what the two share. The fragments use the typed facade of their
 * templates in {@code src/main/jte/comparison}. {@link ApiComparisonController} serves the
 * comparison in the API.
 *
 * <p>The controllers decide nothing about the law. The module's {@code Attributions} finds who made
 * each difference, and the views put it into words.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
