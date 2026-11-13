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
 *   <li>{@link Release}: the release of the app, and the entity tags and {@code Cache-Control} of
 *       the responses that the identities in their address decide.
 * </ul>
 *
 * <p>Every module's pages use it, and it knows nothing of their domain. All of it is
 * infrastructure, the outer ring of the onion, so only the infrastructure of the other modules, for
 * example their {@code web} packages, may use it.
 */
@ApplicationModule(displayName = "Chrome")
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.chrome;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
