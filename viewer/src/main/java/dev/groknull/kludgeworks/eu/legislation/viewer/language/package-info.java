// SPDX-License-Identifier: MIT
/**
 * The EU's official languages. Every module shares {@link Language}. Thus modules that call each
 * other, as the catalogue calls ingest, do not depend on each other for it.
 */
@ApplicationModule(displayName = "Language")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
