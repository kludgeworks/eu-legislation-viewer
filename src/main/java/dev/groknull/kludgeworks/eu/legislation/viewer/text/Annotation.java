// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A box of notes or a caption set apart from the text (Formex ANNOTATION, GR.ANNOTATION).
 *
 * @param provenance The amending acts that put this annotation in its current form.
 * @param heading Its heading, e.g. "NOTES:".
 */
public record Annotation(List<Provenance> provenance, @Nullable Lines heading, List<Block> content)
    implements Block {

  public Annotation {
    provenance = List.copyOf(provenance);
    content = List.copyOf(content);
  }
}
