// SPDX-License-Identifier: MIT
/**
 * Ingest makes the legal text of an expression from the Formex of Cellar, in the background.
 *
 * <p>Another module asks for a text with {@link ConversionRequested}. Ingest queues a job. A worker
 * claims the job, converts the Formex and stores the text in the content module. The worker tells
 * the outcome with an event, {@link TextConverted} or {@link ConversionFailed}, and in the status
 * of the job, which {@link Ingest} gives.
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API of the module and part of its domain ring. The {@code model}
 *       package holds the rest of the domain ring: the ingest job and its refusals.
 *   <li>The {@code application} package is the application ring. It queues and runs jobs.
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
