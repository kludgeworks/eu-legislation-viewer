// SPDX-License-Identifier: MIT
/**
 * The web side of the content module.
 *
 * <ul>
 *   <li>{@link LegalRenderer} renders legal texts as HTML, with the templates in {@code
 *       src/main/jte/legal}.
 *   <li>{@link ContentController} serves the fragments that pages transclude, by root.
 * </ul>
 *
 * <p>It is a named interface of its own ({@code web}). A module that shows legal text as the
 * content module renders it uses this interface. For example, the Changes view of the comparison.
 */
@InfrastructureRing
@NamedInterface("web")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
