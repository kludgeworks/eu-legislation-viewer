// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** An annotation: a notes box or a form's caption. */
public record Annotation(List<Provenance> provenance, @Nullable Lines heading, List<Block> content)
    implements Block {

  public Annotation {
    provenance = List.copyOf(provenance);
    content = List.copyOf(content);
  }
}
