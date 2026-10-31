// SPDX-License-Identifier: MIT
/**
 * The acts module's web adapter: an act as a resource at its CELEX (ActController, in HAL, an act
 * by its ELI a lookup), links to an act's resources for every module (ActLinks), and, on the API's
 * root, the act's entry points (RootLinks). A named interface, so other modules' web adapters link
 * to an act's resources as this one does. An adapter driving the app (jMolecules' primary adapter),
 * so it never uses those the app drives ({@code infra}, the cellar module), nor they it: they meet
 * only in the domain.
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
