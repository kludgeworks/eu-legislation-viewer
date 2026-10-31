// SPDX-License-Identifier: MIT
/**
 * The acts module's web adapter: an act as a resource at its CELEX (ActController, in HAL, an act
 * by its ELI a lookup), links to an act's resources for every module (ActLinks), and the API's
 * vocabulary, as every resource is an act's: its root at /api (ApiController, ApiRoot), with the
 * act's entry points (RootLinks); the viewer's own link relations under the av curie (ApiRelations,
 * HalLinksConfiguration); links at the host and path the request came by, a CELEX's slash as %2F
 * (ApiLinks), which Tomcat is told to pass through (ApiServerConfiguration). A named interface, so
 * other modules' web adapters link to an act's resources, and add to the root, as this one does. An
 * adapter driving the app (jMolecules' primary adapter), so it never uses those the app drives
 * ({@code infra}, the cellar module), nor they it: they meet only in the domain.
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
