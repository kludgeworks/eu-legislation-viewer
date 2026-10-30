// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Tables that share one caption. */
public record TableGroup(List<Provenance> provenance, List<Block> caption, List<Block> content)
    implements Block {

  public TableGroup {
    provenance = List.copyOf(provenance);
    caption = List.copyOf(caption);
    content = List.copyOf(content);
  }
}
