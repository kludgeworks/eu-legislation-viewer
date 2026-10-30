// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** In the long title: what the act is about. */
public record DocPurpose(List<Inline> content) implements Inline {

  public DocPurpose {
    content = List.copyOf(content);
  }
}
