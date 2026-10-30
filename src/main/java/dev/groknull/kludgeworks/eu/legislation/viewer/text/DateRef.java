// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A date in the text: its value, when the source gives a valid one, and its wording. */
public record DateRef(@Nullable LocalDate date, String asWritten, @Nullable String role)
    implements Inline {}
