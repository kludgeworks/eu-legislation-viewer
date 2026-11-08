// SPDX-License-Identifier: MIT
/**
 * The web side of the catalogue module. It serves the pages of an act: the page of a version,
 * {@link VersionController}, and the compare page, {@link CompareController}, with the preparation
 * that both show while the act is prepared. The pages use the typed facade of their templates in
 * {@code src/main/jte/catalogue}.
 *
 * <p>Other modules fill parts of the pages, by address: htmx transcludes the text of a version from
 * the content module, and the changes and the counted acts of a comparison from the comparison
 * module. The pages know those modules only by the addresses of their parts. No Java of another
 * module's web side is used here.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
