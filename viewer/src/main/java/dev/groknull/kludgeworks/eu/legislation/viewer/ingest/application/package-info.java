// SPDX-License-Identifier: MIT
/**
 * The ingest module's application ring: scheduling expressions (IngestService) and running their
 * jobs as they are requested (JobRunner), on the conversions the infrastructure provides
 * (Conversions).
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
