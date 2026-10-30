// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * Content an amendment deleted but the consolidation keeps. Never live text: it is in force no
 * longer, and its provenance names the act that deleted it.
 *
 * @param provenance The deleting act.
 */
public record DeletedBlock(List<Provenance> provenance, List<Block> content) implements Block {

  public DeletedBlock {
    provenance = List.copyOf(provenance);
    content = List.copyOf(content);
  }
}
