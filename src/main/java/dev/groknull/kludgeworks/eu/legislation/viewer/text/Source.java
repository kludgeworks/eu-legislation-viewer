// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/**
 * The document converted: its CELEX number (for a consolidation, with its date), ELI and OJ
 * publication.
 */
public record Source(String celex, @Nullable String eli, @Nullable Publication publication) {}
