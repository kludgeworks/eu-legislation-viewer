// SPDX-License-Identifier: MIT
/**
 * Ingest makes the legal text of an expression from the Formex of Cellar, in the background.
 *
 * <p>When {@link Ingest} is asked to build an expression, it queues a job. A worker claims the job,
 * converts the Formex of the expression and stores the text in the content module. The worker tells
 * the outcome with an event, {@link ExpressionBuilt} or {@link ExpressionFailed}, and in the status
 * of the job.
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. The {@code model}
 *       package holds the rest of the domain ring: the ingest job and its refusals.
 *   <li>The {@code application} package is the application ring. It schedules and runs jobs.
 *   <li>The {@code infra} package is the infrastructure. It reads the Formex from Cellar and runs
 *       the stylesheets of //formex-converter.
 * </ul>
 */
@ApplicationModule(displayName = "Ingest")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
