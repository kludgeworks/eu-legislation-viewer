// SPDX-License-Identifier: MIT
/**
 * The API's entry point (URLs and API): {@code /api}, the root resource a client starts from, to
 * which each module adds what it can reach, as URI templates (ApiRoot); and the curie naming the
 * viewer's own link relations ({@code av:versions}; IANA's stay unprefixed: ApiRelations). Every
 * other resource is at its ELI, owned by its module. It uses no module; they use it. An adapter
 * driving the app (jMolecules' primary adapter), as the modules' {@code web}.
 */
@ApplicationModule(displayName = "API")
@InfrastructureRing
@PrimaryAdapter
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
