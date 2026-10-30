// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/** An image in running text. */
public record InlineImage(String src, @Nullable ImageContent content) implements Inline {}
