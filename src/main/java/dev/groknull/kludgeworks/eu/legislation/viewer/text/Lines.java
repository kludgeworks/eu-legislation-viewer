// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/**
 * A heading, subheading or signatory line: inline content, several lines joined by {@link Eol}
 * ({@link InlineLines}); or, where the source's lines hold blocks, those blocks ({@link
 * BlockLines}).
 */
public sealed interface Lines permits InlineLines, BlockLines {}
