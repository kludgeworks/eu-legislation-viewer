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
 *       at {@code /content/{root}}, which pages transclude.
 * </ul>
 *
 * <p>It renders the content module's {@code Element} and reads texts through the content module's
 * API, and holds no state. All of it is infrastructure, the outer ring of the onion, so only the
 * infrastructure of the other modules, for example their {@code web} packages, may use it.
 */
@ApplicationModule(displayName = "Legal text")
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
