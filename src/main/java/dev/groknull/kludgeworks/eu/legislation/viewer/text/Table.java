// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A table.
 *
 * @param provenance The amending acts that put this table in its current form, when they differ
 *     from its unit's.
 * @param caption The table's title.
 * @param cols The number of columns.
 */
public record Table(
    List<Provenance> provenance, List<Block> caption, @Nullable Integer cols, List<TableRow> rows)
    implements Block {

  public Table {
    provenance = List.copyOf(provenance);
    caption = List.copyOf(caption);
    rows = List.copyOf(rows);
  }
}
