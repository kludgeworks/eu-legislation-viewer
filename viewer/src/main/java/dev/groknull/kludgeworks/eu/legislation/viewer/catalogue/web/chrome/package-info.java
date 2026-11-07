// SPDX-License-Identifier: MIT
/**
 * What every page has around its content, {@link Chrome}. Controllers make it from the request and
 * pass it to the layout. It holds the colour theme of the reader and the location of the page.
 * {@code POST /theme} sets the theme, in {@link ThemeController}.
 *
 * <p>This package is for the whole site, not only for the catalogue. It is here because the
 * catalogue has the only pages for now.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
