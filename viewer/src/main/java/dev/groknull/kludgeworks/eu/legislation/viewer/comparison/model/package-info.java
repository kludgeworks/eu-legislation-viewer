// SPDX-License-Identifier: MIT
/**
 * The rules of a comparison. Each class makes one decision.
 *
 * <ul>
 *   <li>{@link Wording} tells what a block says, without its markers, and gives its words.
 *   <li>{@link Units} holds the units of a text by their eIds, and the own blocks of each unit.
 *   <li>{@link Differ} finds the differences, by eId, then by block, then by word.
 * </ul>
 *
 * <p>These are pure functions on the elements of the content module.
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
