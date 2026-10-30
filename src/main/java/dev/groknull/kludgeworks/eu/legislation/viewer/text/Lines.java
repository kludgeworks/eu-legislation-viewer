// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/**
 * A heading, a subheading or a signatory's line: inline content, several lines joined by eol. Where
 * the source's lines hold blocks (a numbered paragraph with a second paragraph, a list, a table, an
 * image with its caption, a signature in each language), the blocks instead, in content.
 */
public sealed interface Lines permits InlineLines, BlockLines {}
