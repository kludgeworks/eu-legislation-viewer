// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A row of a table, or a group of rows under a heading. */
public sealed interface TableRow {

  /**
   * A table row.
   *
   * @param provenance The amending acts that put this row in its current form.
   * @param header A heading row.
   * @param cells A cell.
   */
  record Row(List<Provenance> provenance, boolean header, List<Cell> cells) implements TableRow {

    public Row {
      provenance = List.copyOf(provenance);
      cells = List.copyOf(cells);
    }
  }

  /**
   * A group of rows with a heading row (Formex BLK with TI.BLK and STI.BLK, used in checklists and
   * budgets). No AKN equivalent.
   *
   * @param num The heading row's number, e.g. "1.1.".
   * @param heading The heading row's text.
   * @param subheading The heading row's subtitle (Formex STI.BLK).
   */
  record RowGroup(
      List<Inline> num, @Nullable Lines heading, @Nullable Lines subheading, List<TableRow> rows)
      implements TableRow {

    public RowGroup {
      num = List.copyOf(num);
      rows = List.copyOf(rows);
    }
  }

  /**
   * A table cell, holding blocks; empty for an empty cell.
   *
   * @param colspan The number of columns the cell spans.
   * @param rowspan The number of rows the cell spans.
   * @param header A heading cell.
   */
  record Cell(
      @Nullable Integer colspan, @Nullable Integer rowspan, boolean header, List<Block> content) {

    public Cell {
      content = List.copyOf(content);
    }
  }
}
