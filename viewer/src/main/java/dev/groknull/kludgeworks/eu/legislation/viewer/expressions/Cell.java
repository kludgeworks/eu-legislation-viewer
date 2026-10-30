// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table cell, holding blocks; empty for an empty cell.
 *
 * @param colspan The number of columns the cell spans.
 * @param rowspan The number of rows the cell spans.
 * @param header A heading cell.
 */
public record Cell(
    @Nullable Integer colspan, @Nullable Integer rowspan, boolean header, List<Block> content) {

  public Cell {
    content = List.copyOf(content);
  }
}
