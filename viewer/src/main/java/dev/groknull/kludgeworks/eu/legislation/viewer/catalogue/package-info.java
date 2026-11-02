// SPDX-License-Identifier: MIT
/**
 * Acts: an act (a work, identified by CELEX and ELI), its versions and its timeline, and which
 * version applies on a date (Design: Choosing the version to show). The root package is the
 * module's API and the onion's domain ring; {@link Acts} is the repository of all acts, which other
 * modules use directly; its adapter on Cellar is in {@code infra}.
 */
@ApplicationModule(displayName = "Catalogue")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
