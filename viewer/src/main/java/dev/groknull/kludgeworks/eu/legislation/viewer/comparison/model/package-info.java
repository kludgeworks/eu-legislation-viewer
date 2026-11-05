// SPDX-License-Identifier: MIT
/**
 * The rules of a comparison, one decision a class.
 *
 * <ul>
 *   <li>Wording: what a block says, its markers left out, and its words;
 *   <li>Units: a text's units by their eIds, and each one's own blocks;
 *   <li>Differ: the differences, by eId, then block, then word.
 * </ul>
 *
 * <p>Pure functions on the content module's elements.
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
