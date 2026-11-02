// SPDX-License-Identifier: MIT
/**
 * The EU's official languages: shared by every module (Language), so that modules calling each
 * other, as the catalogue calls ingest, don't depend on each other for it.
 */
@ApplicationModule(displayName = "Language")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
