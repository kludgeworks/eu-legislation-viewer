// SPDX-License-Identifier: MIT
/**
 * Legal text as HTML.
 *
 * <ul>
 *   <li>{@link LegalRenderer} renders a legal text, an element at a time, with the templates in
 *       {@code src/main/jte/legal}.
 *   <li>{@link JournalHeader} is the header of the Official Journal over a text, which {@code
 *       src/main/jte/legalpart} shows.
 * </ul>
 *
 * <p>It is a library, not a module of the viewer. The pages of several modules show legal text: the
 * content module's fragments, and the comparison page. It renders the content module's {@code
 * Element}, and holds no state. The viewer registers its renderer with {@link
 * LegalTextConfiguration}, because its package is outside the packages that the viewer scans.
 */
@InfrastructureRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.legaltext;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
