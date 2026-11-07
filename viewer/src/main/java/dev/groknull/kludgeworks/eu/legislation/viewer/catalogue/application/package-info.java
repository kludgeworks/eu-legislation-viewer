// SPDX-License-Identifier: MIT
/**
 * The application ring of the catalogue module.
 *
 * <ul>
 *   <li>{@link CatalogueService} and {@link ActRefresher} get the acts from the store. They read an
 *       act from Cellar the first time, and again when it is due.
 *   <li>{@link TextService} prepares the texts of the versions.
 *   <li>The infrastructure provides the stores and the source: {@link ActStore}, {@link ActSource},
 *       {@link ExpressionStore} and {@link PreparationStore}.
 * </ul>
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
