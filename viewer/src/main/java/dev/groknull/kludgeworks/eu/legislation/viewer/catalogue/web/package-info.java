// SPDX-License-Identifier: MIT
/**
 * The web side of the catalogue module. It serves the pages of the catalogue, for example the page
 * of a version, {@link VersionController}. The pages use the typed facade of their templates in
 * {@code src/main/jte/catalogue}. htmx transcludes the text of a page from the fragments of the
 * content module.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
