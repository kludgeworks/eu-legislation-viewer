// SPDX-License-Identifier: MIT
/**
 * The ingest module's domain: the ingest job (IngestJob) and its queue (IngestJobs), and the
 * converter's refusals (ConversionException).
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
