// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A table row. */
public record Row(List<Provenance> provenance, boolean header, List<Cell> cells)
    implements TableRow {

  public Row {
    provenance = List.copyOf(provenance);
    cells = List.copyOf(cells);
  }
}
