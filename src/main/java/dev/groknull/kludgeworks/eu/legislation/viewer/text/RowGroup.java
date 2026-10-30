// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A group of rows with a heading row (Formex BLK with TI.BLK and STI.BLK, used in checklists and
 * budgets). No AKN equivalent.
 *
 * @param num The heading row's number, e.g. "1.1.".
 * @param heading The heading row's text.
 * @param subheading The heading row's subtitle (Formex STI.BLK).
 */
public record RowGroup(
    List<Inline> num, @Nullable Lines heading, @Nullable Lines subheading, List<TableRow> rows)
    implements TableRow {

  public RowGroup {
    num = List.copyOf(num);
    rows = List.copyOf(rows);
  }
}
