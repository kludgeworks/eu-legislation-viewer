// SPDX-License-Identifier: MIT
/**
 * The catalogue module's infrastructure: acts read from Cellar's SPARQL endpoint (CellarActs), and
 * kept in Postgres (JooqActStore), on the tables jOOQ generates from the module's migrations
 * (jooq).
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
