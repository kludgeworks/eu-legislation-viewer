// SPDX-License-Identifier: MIT
/**
 * The infrastructure of the catalogue module. {@link CellarActs} reads acts from Cellar's SPARQL
 * endpoint. {@link JooqActStore} keeps them in Postgres. It uses the tables that jOOQ generates
 * from the migrations of the module, in the {@code jooq} package.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
