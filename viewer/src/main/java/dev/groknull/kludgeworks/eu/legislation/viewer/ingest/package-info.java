// SPDX-License-Identifier: MIT
/**
 * Ingest: an expression's legal text made from Cellar's Formex. The root package is the module's
 * API and the onion's domain ring (the ingest job, to come); in {@code infra}, internal to the
 * module, the Formex read from Cellar (CellarFormexSource) and the adapter on //formex-converter's
 * stylesheets (FormexConverter).
 */
@ApplicationModule(displayName = "Ingest")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
