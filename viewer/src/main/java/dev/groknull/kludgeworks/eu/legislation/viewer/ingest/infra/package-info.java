// SPDX-License-Identifier: MIT
/**
 * The infrastructure of the ingest module.
 *
 * <ul>
 *   <li>{@link CellarFormexSource} reads the Formex of a document from Cellar.
 *   <li>{@link FormexConverter} converts it with the stylesheets of //formex-converter.
 *   <li>{@link FormexConversion} runs only a few conversions at a time.
 *   <li>{@link JooqIngestJobs} keeps the jobs in Postgres. It uses the tables that jOOQ generates
 *       from the migrations of the module, in the {@code jooq} package.
 * </ul>
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
