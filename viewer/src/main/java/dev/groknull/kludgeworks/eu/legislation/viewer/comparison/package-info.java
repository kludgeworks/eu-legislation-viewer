// SPDX-License-Identifier: MIT
/**
 * Comparisons: how two versions' texts differ, provision by provision (Design: Mode: Compare).
 * Texts are known by their roots in the content module, the only module this one uses, so a
 * comparison is of two texts, whatever acts and versions they are; which versions to compare is the
 * catalogue's to say.
 *
 * <p>A sliced onion: the root package is the module's API and part of its domain ring ({@link
 * Comparisons}, a {@link Comparison} and its differences, {@link ComparisonWanted}), with the rules
 * that find them ({@code model}); comparing two stored texts, and keeping the comparison, is the
 * application ring ({@code application}); the comparisons in Postgres are the infrastructure
 * ({@code infra}).
 */
@ApplicationModule(displayName = "Comparison", allowedDependencies = "content")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
