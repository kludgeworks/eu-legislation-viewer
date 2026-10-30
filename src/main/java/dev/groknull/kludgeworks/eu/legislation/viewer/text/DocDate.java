// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** In the long title: the act's date. */
public record DocDate(@Nullable LocalDate date, String asWritten, @Nullable String role)
    implements Inline {}
