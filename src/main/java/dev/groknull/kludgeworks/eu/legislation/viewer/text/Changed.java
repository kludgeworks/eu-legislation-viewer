// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** Text an amending act changed inside a block. */
public record Changed(Provenance provenance, List<Inline> content) implements Inline {

  public Changed {
    content = List.copyOf(content);
  }
}
