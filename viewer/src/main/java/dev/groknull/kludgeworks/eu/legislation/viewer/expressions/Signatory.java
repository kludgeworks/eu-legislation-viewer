// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import org.jspecify.annotations.Nullable;

/**
 * A signatory, its lines split by position.
 *
 * @param organization The first line: "For the Commission".
 * @param role The lines in between: "The President".
 * @param person The last line: the name.
 */
public record Signatory(
    @Nullable Lines organization, @Nullable Lines role, @Nullable Lines person) {}
