// SPDX-License-Identifier: MIT
/**
 * The catalogue module's application ring: the acts from the store, read from Cellar the first time
 * (CatalogueService), on the store and the source the infrastructure provides (ActStore,
 * ActSource).
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
