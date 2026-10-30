// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

/**
 * Tables that belong together under one title (Formex GR.TBL).
 *
 * @param provenance The amending acts that put this group in its current form.
 * @param caption The group's title.
 */
public record TableGroup(List<Provenance> provenance, List<Block> caption, List<Block> content)
    implements Block {

  public TableGroup {
    provenance = List.copyOf(provenance);
    caption = List.copyOf(caption);
    content = List.copyOf(content);
  }
}
