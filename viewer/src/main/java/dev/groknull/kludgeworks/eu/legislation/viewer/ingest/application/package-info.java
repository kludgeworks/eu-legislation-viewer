// SPDX-License-Identifier: MIT
/**
 * The application ring of the ingest module.
 *
 * <ul>
 *   <li>{@link IngestService} schedules expressions.
 *   <li>{@link JobRunner} runs their jobs when they are requested.
 *   <li>{@link Conversions} is the conversion that the infrastructure provides.
 * </ul>
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
