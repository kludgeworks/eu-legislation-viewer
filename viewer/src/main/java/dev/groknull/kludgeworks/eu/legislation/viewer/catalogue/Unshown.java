// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Days on which the law is a text that no version shows: the text of a version with the changes of
 * one act that applies before the next version includes it. For example, from 1 to 6 June 2017 the
 * law of 2015/1998 is its version of 29 February 2016 with the changes of 2017/815.
 *
 * @param act the act whose changes the text of the version lacks
 * @param first the first of the days
 * @param last the last of the days
 */
@ValueObject
public record Unshown(ActIdentifier act, LocalDate first, LocalDate last) {}
