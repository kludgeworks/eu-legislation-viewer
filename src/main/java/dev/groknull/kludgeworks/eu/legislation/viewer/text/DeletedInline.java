// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Text an amendment deleted but the consolidation keeps: never live text. */
public record DeletedInline(Provenance provenance, List<Inline> content) implements Inline {

  public DeletedInline {
    content = List.copyOf(content);
  }
}
