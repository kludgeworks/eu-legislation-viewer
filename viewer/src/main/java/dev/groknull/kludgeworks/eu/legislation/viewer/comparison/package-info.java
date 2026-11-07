// SPDX-License-Identifier: MIT
/**
 * Comparisons tell how the texts of two versions differ, provision by provision.
 *
 * <p>Texts are known by their roots in the content module. This module uses no other module. Thus a
 * comparison is of two texts, whatever acts and versions they are. The catalogue decides which
 * versions to compare and which acts made a difference. The catalogue shows the Changes view on the
 * page of an act.
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. It holds {@link
 *       Comparisons}, a {@link Comparison} with its differences, and {@link ComparisonWanted}. The
 *       {@code model} package holds the rules that find the differences.
 *   <li>The {@code application} package is the application ring. It compares two stored texts and
 *       keeps the comparison.
 *   <li>The infrastructure keeps the comparisons in Postgres. It also holds what the Changes view
 *       of a comparison shows, in {@code web.view}. That package is a named interface of its own,
 *       for the page of the catalogue.
 * </ul>
 */
@ApplicationModule(displayName = "Comparison", allowedDependencies = "content")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
