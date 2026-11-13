// SPDX-License-Identifier: MIT
/**
 * The infrastructure of the content module. {@link JooqTextStore} keeps the texts in Postgres, one
 * row for each. It uses the tables that jOOQ generates from the migrations of the module, in the
 * {@code jooq} package. {@link OutlineCacheConfiguration} keeps the outlines read last in the
 * process.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
