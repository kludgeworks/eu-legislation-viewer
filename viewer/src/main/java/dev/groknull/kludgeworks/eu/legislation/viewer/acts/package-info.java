// SPDX-License-Identifier: MIT
/**
 * Acts: an act (a work, identified by CELEX and ELI), its versions and its timeline, and which
 * version applies on a date (Design: Choosing the version to show). The root package is the
 * module's API and the onion's domain ring; {@link Acts} is its application service; Cellar's
 * adapter is in {@code infra}.
 */
@ApplicationModule(displayName = "Acts")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
