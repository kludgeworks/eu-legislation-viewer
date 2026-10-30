// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A table: its caption and rows. */
public record Table(
    List<Provenance> provenance, List<Block> caption, @Nullable Integer cols, List<TableRow> rows)
    implements Block {

  public Table {
    provenance = List.copyOf(provenance);
    caption = List.copyOf(caption);
    rows = List.copyOf(rows);
  }
}
