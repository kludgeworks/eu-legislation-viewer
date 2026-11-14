// SPDX-License-Identifier: MIT
/**
 * Legal text as HTML, for Java and for the browser.
 *
 * <ul>
 *   <li>{@link LegalRenderer} renders a legal text, an element at a time, with the templates in
 *       {@code src/main/jte/legal}. The comparison's pages use it.
 *   <li>{@link JournalHeader} is the header of the Official Journal over a text, which {@code
 *       src/main/jte/legalpart} shows.
 *   <li>{@link LegalTextController} serves a stored text, or some of its units, as an htmx fragment
 *       at {@code /content/{id}}, which pages transclude. Browsers ask again each time, and get 304
 *       while the release and the text are the same.
 *   <li>{@link RenderedTexts} renders texts, whole or a run of their units, and keeps their HTML in
 *       Valkey, by the release and the identity of the text ({@link HtmlCacheConfiguration}).
 * </ul>
 *
 * <p>It renders the content module's {@code Element} and reads texts through the content module's
 * API, and holds no state but the cache of its HTML. All of it is infrastructure, the outer ring of
 * the onion, so only the infrastructure of the other modules, for example their {@code web}
 * packages, may use it.
 */
@ApplicationModule(displayName = "Legal text")
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
