// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/**
 * Which document this is, and where it was published.
 *
 * @param celex The CELEX number of this document: 02015R1998-20260322 for a consolidated version,
 *     32015R1998 for the original.
 * @param eli The act's ELI, when the source carries it (Formex 06.x).
 * @param publication Where the act was published in the Official Journal.
 */
public record Source(String celex, @Nullable String eli, @Nullable Publication publication) {}
