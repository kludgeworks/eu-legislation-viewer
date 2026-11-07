// SPDX-License-Identifier: MIT
/**
 * The infrastructure of the comparison module. {@link JooqComparisonStore} keeps the comparisons in
 * Postgres. It uses the table that jOOQ generates from the migrations of the module, in the {@code
 * jooq} package.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
