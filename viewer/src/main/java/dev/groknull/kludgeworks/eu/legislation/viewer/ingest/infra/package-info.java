// SPDX-License-Identifier: MIT
/**
 * The ingest module's infrastructure: a document's Formex read from Cellar (CellarFormexSource) and
 * converted by //formex-converter's stylesheets (FormexConverter), a few at a time
 * (FormexConversion); the job queue in Postgres (JooqIngestJobs), on the tables jOOQ generates from
 * the module's migrations (jooq).
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
