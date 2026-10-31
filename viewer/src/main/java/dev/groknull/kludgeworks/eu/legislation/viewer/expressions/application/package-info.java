// SPDX-License-Identifier: MIT
/**
 * The expressions module's use cases, the onion's application ring: they combine the domain's
 * repositories (acts' and its own) into what the module's clients ask for, such as the expression
 * that applies on a date (PointInTime). Used by the infrastructure's API adapters (the web), never
 * by the domain.
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
