// SPDX-License-Identifier: MIT
/**
 * The application ring of the catalogue module.
 *
 * <ul>
 *   <li>{@link CatalogueService} gets the acts from the store. It reads an act from Cellar the
 *       first time.
 *   <li>{@link ChangeScan} asks Cellar what changed, and {@link ActRefresher} reads each held act
 *       that changed again. It publishes one event for each difference.
 *   <li>{@link TextService} prepares the texts of the versions.
 *   <li>The infrastructure provides the stores and the sources: {@link ActStore}, {@link
 *       ActSource}, {@link ChangeStore}, {@link ChangeSource}, {@link ExpressionStore} and {@link
 *       PreparationStore}.
 * </ul>
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
