// SPDX-License-Identifier: MIT
/**
 * The rules of a comparison. Each class makes one decision.
 *
 * <ul>
 *   <li>{@link Wording} tells what a block says, without its markers, and gives its words.
 *   <li>{@link Units} holds the units of a text by their eIds, and the own blocks of each unit.
 *   <li>{@link Pairing} tells which unit of the later text is which unit of the earlier text.
 *   <li>{@link Parts} tells which parts of a unit or a table differ, and {@link Likeness} which of
 *       them are one part, changed.
 *   <li>{@link Marks} marks the words that differ.
 *   <li>{@link Markers} tells which acts put a unit in its form.
 *   <li>{@link Differ} finds the differences, by unit, then by block, then by word, and sets them
 *       out in the order of the later text.
 * </ul>
 *
 * <p>These are pure functions on the elements of the content module.
 */
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
