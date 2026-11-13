// SPDX-License-Identifier: MIT
/**
 * Comparisons tell how the texts of two versions differ, provision by provision. The module also
 * owns the comparison page, which shows them.
 *
 * <p>Texts are known by their identities in the content module, so a comparison is of two texts,
 * whatever acts and versions they are. The page knows the acts and versions from the catalogue's
 * API: which versions to compare and which acts made a difference. When the catalogue tells that
 * the texts of two neighbouring versions are built, the module works out their comparison before a
 * reader asks for it.
 *
 * <p>A comparison is kept with the version of the rules that worked it out, which the build takes
 * from their sources. A kept comparison of another version is worked out again when it is read.
 * When the content module replaces a text, the module deletes the comparisons of the old one.
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. It holds {@link
 *       Comparisons} and a {@link Comparison} with its differences. The {@code model} package holds
 *       the rules that find the differences.
 *   <li>The {@code application} package is the application ring. It compares two stored texts and
 *       keeps the comparison.
 *   <li>The infrastructure keeps the comparisons in Postgres. It also serves the comparison page,
 *       in {@code web}, with what the page shows worked out in {@code web.view}.
 * </ul>
 */
@ApplicationModule(displayName = "Comparison")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
