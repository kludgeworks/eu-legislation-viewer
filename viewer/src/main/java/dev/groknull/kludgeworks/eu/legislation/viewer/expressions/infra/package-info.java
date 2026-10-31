// SPDX-License-Identifier: MIT
/**
 * The expressions module's adapters: expressions kept in Valkey (ValkeyExpressions), converted once
 * from Cellar's Formex (FormexConversion, on a FormexSource, by FormexConverter), and the reader
 * from legal-text XML into the records, with its mapper. Adapters the app drives (jMolecules'
 * secondary adapters), apart from those driving it ({@code web}).
 */
@InfrastructureRing
@SecondaryAdapter
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
