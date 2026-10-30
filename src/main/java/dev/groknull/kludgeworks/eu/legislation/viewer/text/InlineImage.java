// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import org.jspecify.annotations.Nullable;

/**
 * An image inside running text or a heading (Formex INCL.ELEMENT TYPE="TIFF" there).
 *
 * @param src The image file in the act's Cellar manifestation.
 * @param content What the image shows (Formex INCL.ELEMENT CONTENT).
 */
public record InlineImage(String src, @Nullable ImageContent content) implements Inline {}
