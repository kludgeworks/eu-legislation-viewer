// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * A paragraph of running text.
 *
 * @param provenance The amending acts that put this paragraph in its current form, when they differ
 *     from its unit's.
 * @param num The label of a numbered paragraph outside the hierarchy, e.g. in a table cell.
 */
public record Paragraph(List<Provenance> provenance, List<Inline> num, List<Inline> content)
    implements Block {

  public Paragraph {
    provenance = List.copyOf(provenance);
    num = List.copyOf(num);
    content = List.copyOf(content);
  }
}
