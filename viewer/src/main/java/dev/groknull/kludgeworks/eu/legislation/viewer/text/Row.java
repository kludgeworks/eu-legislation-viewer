// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/**
 * A table row.
 *
 * @param provenance The amending acts that put this row in its current form.
 * @param header A heading row.
 * @param cells A cell.
 */
public record Row(List<Provenance> provenance, boolean header, List<Cell> cells)
    implements TableRow {

  public Row {
    provenance = List.copyOf(provenance);
    cells = List.copyOf(cells);
  }
}
