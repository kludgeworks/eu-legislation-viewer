// SPDX-License-Identifier: MIT
/**
 * What every page of the viewer has around its content, and how every page reads and answers a
 * request.
 *
 * <ul>
 *   <li>{@link Chrome}: controllers make it from the request and pass it to the layout, {@code
 *       src/main/jte/layout}. It holds the colour theme of the reader and the location of the page.
 *       {@code POST /theme} sets the theme, in {@link ThemeController}.
 *   <li>{@link Requests}: the language of a request, today, not found, the media type of a page.
 * </ul>
 *
 * <p>It is a library, not a module of the viewer: every module's pages use it, and it knows nothing
 * of their domain. The viewer registers its controller with {@link ChromeConfiguration}, because
 * its package is outside the packages that the viewer scans.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.chrome;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
