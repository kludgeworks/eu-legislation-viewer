// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A table cell. */
public record Cell(
    @Nullable Integer colspan, @Nullable Integer rowspan, boolean header, List<Block> content) {

  public Cell {
    content = List.copyOf(content);
  }
}
