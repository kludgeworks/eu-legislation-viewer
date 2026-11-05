// SPDX-License-Identifier: MIT
/**
 * The content module's web side: legal texts as HTML (LegalRenderer, by the templates in
 * src/main/jte/legal), and the fragments pages transclude, by root (ContentController). Its own
 * named interface (web), for a module that shows legal text as the content module renders it: the
 * comparison's Changes view.
 */
@InfrastructureRing
@NamedInterface("web")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
