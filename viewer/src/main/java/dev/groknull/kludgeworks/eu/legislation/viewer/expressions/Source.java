// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jspecify.annotations.Nullable;

/**
 * Which document this is, and where it was published.
 *
 * @param celex The CELEX number of this document: 02015R1998-20260322 for a consolidated version,
 *     32015R1998 for the original.
 * @param eli This document's ELI, when its source carries it (from Formex 06.x): the act's original
 *     text's, or the corrigendum's own.
 * @param publication Where the act was published in the Official Journal.
 */
public record Source(String celex, @Nullable String eli, @Nullable Publication publication) {}
