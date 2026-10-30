// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An image (Formex INCL.ELEMENT TYPE="TIFF").
 *
 * @param src The image file in the act's Cellar manifestation.
 * @param content What the image shows (Formex INCL.ELEMENT CONTENT).
 * @param caption The text printed with the image, usually under it (Formex CAPTION).
 * @param description A transcription of the text inside the image, such as the fields of a form
 *     (Formex IMG.CNT): the image's alternative text, and what makes a form searchable.
 */
public record Figure(
    String src, @Nullable ImageContent content, List<Block> caption, List<Block> description)
    implements Block {

  public Figure {
    caption = List.copyOf(caption);
    description = List.copyOf(description);
  }
}
