// SPDX-License-Identifier: MIT
/**
 * The domain of the ingest module.
 *
 * <ul>
 *   <li>{@link IngestJob} is the ingest job, and {@link IngestJobs} holds the jobs.
 *   <li>{@link ExpressionRequested} is the request that queues a job.
 *   <li>{@link ConversionException} and its subclasses are the refusals of the converter.
 * </ul>
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
