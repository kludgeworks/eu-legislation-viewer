// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A date in the text (Formex DATE).
 *
 * @param date The date; absent when the source's value is not a date (Formex DATE ISO="19931911"):
 *     the wording is all there is.
 * @param asWritten The date as printed: "5 November 2015".
 */
public record DateRef(@Nullable LocalDate date, String asWritten) implements Inline {}
