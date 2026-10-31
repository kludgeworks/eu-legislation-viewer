// SPDX-License-Identifier: MIT
/**
 * The acts module's adapters: its catalogue on Cellar's SPARQL endpoint, kept a while in Valkey.
 * Adapters the app drives (jMolecules' secondary adapters), apart from those driving it ({@code
 * web}).
 */
@InfrastructureRing
@SecondaryAdapter
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
