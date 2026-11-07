// SPDX-License-Identifier: MIT
/**
 * Acts. The module holds an act, which is a work identified by CELEX and ELI, with its versions and
 * its timeline. It also tells which version applies on a date.
 *
 * <p>The root package is the API of the module and the domain ring of the onion. {@link Acts} is
 * the repository of all acts, and other modules use it directly. Its adapter on Cellar is in {@code
 * infra}.
 */
@ApplicationModule(displayName = "Catalogue")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
