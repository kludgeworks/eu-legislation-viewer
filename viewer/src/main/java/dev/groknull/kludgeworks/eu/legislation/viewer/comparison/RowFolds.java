// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Folds the rows of a difference's tables that are the same in both texts. A changed table can have
 * thousands of rows and only a few changes. The views send the rows that changed, a row of context
 * on each side, and a fold for each longer run of other rows. A fold loads its rows when the reader
 * opens it.
 *
 * <p>The tables of a unit are numbered from 0, in the order of the text. A table in a cell of
 * another table is not numbered, and its rows are not folded: it is folded or shown with its row.
 * The rows of a table are numbered from 0, in the order of the text, through its row groups. A fold
 * names its rows by these numbers: {@code table="2" first="10" last="40"} stands for rows 10 to 40
 * of the third table. The views give it the address that loads those rows.
 *
 * <ul>
 *   <li>A row with a mark is kept, as is a header row.
 *   <li>Rows that a cell spans are kept or folded together.
 *   <li>A run is folded only within one row group, and only if it has {@link #LEAST} rows or more.
 * </ul>
 *
 * <p>The fold is a {@code diff:rows} element, which the legal text renders as a row of its own. A
 * comparison is kept with its rows folded ({@link Difference#kept}), so folding a unit again leaves
 * a table that has folds as it is.
 */
public final class RowFolds {

  /** The name of a fold of rows. */
  public static final String FOLD = "diff:rows";

  /** The attribute of a fold with the number of its table in the unit. */
  public static final String TABLE = "table";

  /** The attribute of a fold with the number of its first row in its table. */
  public static final String FIRST = "first";

  /** The attribute of a fold with the number of its last row in its table. */
  public static final String LAST = "last";

  /** The rows kept on each side of a row with a mark. */
  static final int CONTEXT = 1;

  /** The fewest rows that are folded. Fewer rows show as they are. */
  static final int LEAST = 3;

  private static final Set<String> MARKS = Set.of("diff:ins", "diff:del");

  private RowFolds() {}

  /**
   * Returns {@code unit} with the runs of rows in its tables folded. A table that has folds already
   * stays as it is.
   */
  public static Element of(Element unit) {
    return new Folding().unit(unit);
  }

  /**
   * Returns the rows {@code first} to {@code last} of table {@code table} of {@code unit}, as they
   * are. Returns none if it has no such table or rows.
   */
  public static List<Element> rows(Element unit, int table, int first, int last) {
    var found = table < 0 ? null : table(unit, table);
    if (found == null) {
      return List.of();
    }
    var all = new ArrayList<Element>();
    rows(found, all);
    if (first < 0 || first > last || last >= all.size()) {
      return List.of();
    }
    return List.copyOf(all.subList(first, last + 1));
  }

  /** Returns the table numbered {@code number}, or null if there is none. */
  private static @Nullable Element table(Element unit, int number) {
    var tables = new ArrayList<Element>();
    tables(unit, tables);
    return number < tables.size() ? tables.get(number) : null;
  }

  /** Adds the tables in {@code element} to {@code tables}, in order, but not those in a row. */
  private static void tables(Element element, List<Element> tables) {
    if (element.name().equals("tr")) {
      return;
    }
    if (element.name().equals("table")) {
      tables.add(element);
    }
    element.children().forEach(child -> tables(child, tables));
  }

  /** Adds the rows of {@code element}, through its row groups, to {@code rows}. */
  private static void rows(Element element, List<Element> rows) {
    for (var child : element.children()) {
      if (child.name().equals("tr")) {
        rows.add(child);
      } else if (child.name().equals("rowGroup")) {
        rows(child, rows);
      }
    }
  }

  private static boolean marked(Element element) {
    return MARKS.contains(element.name()) || element.children().stream().anyMatch(RowFolds::marked);
  }

  /** Folds the tables of one unit, numbering them in the order of the text. */
  private static final class Folding {
    private int tables;

    /** The number of the next row of the table being folded. */
    private int row;

    Element unit(Element element) {
      if (element.name().equals("table")) {
        var number = tables++;
        if (hasFolds(element)) {
          return element;
        }
        var outer = row;
        row = 0;
        var folded = rowsOf(element, number);
        row = outer;
        return folded;
      }
      if (element.children().isEmpty()) {
        return element;
      }
      return copy(element, element.children().stream().map(this::unit).toList());
    }

    /**
     * Returns {@code element}, a table or a row group of table {@code table}, with its runs folded.
     */
    private Element rowsOf(Element element, int table) {
      var children = new ArrayList<Element>();
      var rows = new ArrayList<Element>();
      for (var child : element.children()) {
        if (child.name().equals("tr")) {
          rows.add(child);
          continue;
        }
        children.addAll(folded(rows, table));
        rows.clear();
        if (child.name().equals("rowGroup")) {
          children.add(rowsOf(child, table));
        } else {
          children.add(unit(child));
        }
      }
      children.addAll(folded(rows, table));
      return copy(element, children);
    }

    /**
     * Returns {@code rows}, the rows side by side in one table or row group, with their runs
     * folded. The first is numbered {@link #row}, which moves past them.
     */
    private List<Element> folded(List<Element> rows, int table) {
      var first = row;
      row += rows.size();
      if (rows.isEmpty()) {
        return List.of();
      }
      // The rows that a cell spans stay together, as one block.
      var blocks = new ArrayList<Block>();
      for (var start = 0; start < rows.size(); ) {
        var end = start;
        for (var i = start; i <= end && i < rows.size(); i++) {
          end = Math.max(end, i + span(rows.get(i)) - 1);
        }
        end = Math.min(end, rows.size() - 1);
        blocks.add(new Block(start, end));
        start = end + 1;
      }
      var kept = new boolean[blocks.size()];
      for (var b = 0; b < blocks.size(); b++) {
        for (var i = blocks.get(b).start(); i <= blocks.get(b).end(); i++) {
          var each = rows.get(i);
          if ("true".equals(each.attribute("header")) || marked(each)) {
            for (var near = Math.max(0, b - CONTEXT);
                near <= Math.min(blocks.size() - 1, b + CONTEXT);
                near++) {
              kept[near] = true;
            }
            break;
          }
        }
      }
      var out = new ArrayList<Element>();
      for (var b = 0; b < blocks.size(); ) {
        if (kept[b]) {
          out.addAll(rows.subList(blocks.get(b).start(), blocks.get(b).end() + 1));
          b++;
          continue;
        }
        var end = b;
        while (end + 1 < blocks.size() && !kept[end + 1]) {
          end++;
        }
        var from = blocks.get(b).start();
        var to = blocks.get(end).end();
        if (to - from + 1 < LEAST) {
          out.addAll(rows.subList(from, to + 1));
        } else {
          out.add(fold(width(rows), table, first + from, first + to));
        }
        b = end + 1;
      }
      return out;
    }

    /** Returns the fold of rows {@code from} to {@code to} of table {@code table}. */
    private Element fold(int columns, int table, int from, int to) {
      var attributes =
          Map.of(
              "rows",
              String.valueOf(to - from + 1),
              "columns",
              String.valueOf(columns),
              TABLE,
              String.valueOf(table),
              FIRST,
              String.valueOf(from),
              LAST,
              String.valueOf(to));
      return new Element(FOLD, attributes, List.of(), "");
    }
  }

  /** Returns whether {@code table} has folds, in it or in its row groups. */
  private static boolean hasFolds(Element table) {
    return table.children().stream()
        .anyMatch(
            child ->
                child.name().equals(FOLD) || (child.name().equals("rowGroup") && hasFolds(child)));
  }

  /**
   * Rows that a cell spans, kept or folded together: {@code start} to {@code end}, of their run.
   */
  private record Block(int start, int end) {}

  /** Returns how many columns the widest of {@code rows} spans, each cell its colspan. */
  private static int width(List<Element> rows) {
    var widest = 1;
    for (var row : rows) {
      var width = 0;
      for (var cell : row.children()) {
        width += number(cell.attribute("colspan"));
      }
      widest = Math.max(widest, width);
    }
    return widest;
  }

  /** Returns how many rows {@code row} spans: the most rows that one of its cells spans. */
  private static int span(Element row) {
    var most = 1;
    for (var cell : row.children()) {
      most = Math.max(most, number(cell.attribute("rowspan")));
    }
    return most;
  }

  /** Returns a colspan or a rowspan. One if there is none. */
  private static int number(@Nullable String span) {
    return span == null ? 1 : Integer.parseInt(span);
  }

  private static Element copy(Element element, List<Element> children) {
    return new Element(element.name(), element.attributes(), children, element.text());
  }
}
