// SPDX-License-Identifier: MIT
/**
 * The text module's use cases, the onion's application ring: they combine the domain's repositories
 * (acts' and text's own) into what the module's clients ask for, such as the text that applies on a
 * date (PointInTime). Used by the infrastructure's API adapters (the web), never by the domain.
 */
@ApplicationRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.text.application;

import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.jspecify.annotations.NullMarked;
