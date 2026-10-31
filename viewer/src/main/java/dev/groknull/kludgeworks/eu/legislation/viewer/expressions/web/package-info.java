// SPDX-License-Identifier: MIT
/**
 * The expressions module's web adapter: the legal text as JSON (LegalTextJsonModule), and, to come,
 * a version's resource. An adapter driving the app (jMolecules' primary adapter), apart from those
 * the app drives ({@code infra}).
 */
@InfrastructureRing
@PrimaryAdapter
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.jspecify.annotations.NullMarked;
