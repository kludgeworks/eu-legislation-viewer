// SPDX-License-Identifier: MIT
/**
 * Ingest: an expression's legal text made from Cellar's Formex, in the background. Asked to build
 * one (Ingest), it queues a job; a worker claims it, converts the expression's Formex and stores
 * the text (the content module), and says how it went with an event (ExpressionBuilt,
 * ExpressionFailed) and in the job's status.
 *
 * <p>A sliced onion: the root package is the module's API and part of its domain ring, with the
 * ingest job and its refusals ({@code model}); scheduling and running jobs is the application ring
 * ({@code application}); Cellar's Formex and //formex-converter's stylesheets are the
 * infrastructure ({@code infra}).
 */
@ApplicationModule(displayName = "Ingest")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
