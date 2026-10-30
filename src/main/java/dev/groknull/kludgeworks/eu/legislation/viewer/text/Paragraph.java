// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A paragraph of running text, with the number of a numbered paragraph outside the hierarchy. */
public record Paragraph(List<Provenance> provenance, List<Inline> num, List<Inline> content)
    implements Block {

  public Paragraph {
    provenance = List.copyOf(provenance);
    num = List.copyOf(num);
    content = List.copyOf(content);
  }
}
