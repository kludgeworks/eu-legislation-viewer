// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Blocks an amendment deleted but the consolidation keeps: never live text. */
public record DeletedBlock(List<Provenance> provenance, List<Block> content) implements Block {

  public DeletedBlock {
    provenance = List.copyOf(provenance);
    content = List.copyOf(content);
  }
}
