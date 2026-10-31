// SPDX-License-Identifier: MIT
/**
 * The acts module's web adapter: an act as a resource at its ELI (ActController, in HAL), and the
 * ELI in a request's path for every route under {@code /eli}: its two shapes (EliPaths), read into
 * an {@link dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli} argument, and links to an act
 * (ActLinks); and, on the API's root, an act by its ELI (RootLinks). A named interface, so other
 * modules' web adapters route and link by ELI as this one does. An adapter driving the app
 * (jMolecules' primary adapter), so it never uses those the app drives ({@code infra}, the cellar
 * module), nor they it: they meet only in the domain.
 */
@InfrastructureRing
@PrimaryAdapter
@NamedInterface("web")
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.NamedInterface;
