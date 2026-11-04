// SPDX-License-Identifier: MIT
/**
 * What every page has around it (Chrome, which controllers make from the request and pass to the
 * layout): the reader's colour theme, set by POST /theme (ThemeController), and where the page is.
 * Site-wide, not the catalogue's: here while the catalogue has the only pages.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
