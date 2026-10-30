// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Where the act was published in the Official Journal. */
public record Publication(
    String series,
    @Nullable String number,
    @Nullable LocalDate date,
    @Nullable String page,
    @Nullable String document) {}
