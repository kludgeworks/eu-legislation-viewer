// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Rows under a heading inside a table. */
public record RowGroup(
    List<Inline> num, @Nullable Lines heading, @Nullable Lines subheading, List<TableRow> rows)
    implements TableRow {

  public RowGroup {
    num = List.copyOf(num);
    rows = List.copyOf(rows);
  }
}
