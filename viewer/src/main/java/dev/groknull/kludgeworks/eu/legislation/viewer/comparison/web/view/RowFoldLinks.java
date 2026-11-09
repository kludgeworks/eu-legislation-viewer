// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.RowFolds;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.HashMap;
import java.util.List;

/**
 * Gives each fold of rows ({@link RowFolds}) in a difference the address that loads its rows. For
 * example, the address of a difference with {@code &table=2&rows=10-40} gives rows 10 to 40 of its
 * third table.
 */
public final class RowFoldLinks {
  /** The attribute of a fold with the address of its rows. The legal text renders it. */
  static final String HREF = "href";

  private RowFoldLinks() {}

  /**
   * Returns {@code unit} with the runs of rows in its tables folded, if they are not yet, and each
   * fold loading its rows from {@code address}, the address of the difference.
   */
  public static Element of(Element unit, String address) {
    return linked(RowFolds.of(unit), address);
  }

  private static Element linked(Element element, String address) {
    if (element.name().equals(RowFolds.FOLD)) {
      var attributes = new HashMap<>(element.attributes());
      attributes.put(
          HREF,
          address
              + "&"
              + ComparePage.TABLE
              + "="
              + element.attribute(RowFolds.TABLE)
              + "&"
              + ComparePage.ROWS
              + "="
              + element.attribute(RowFolds.FIRST)
              + "-"
              + element.attribute(RowFolds.LAST));
      return new Element(element.name(), attributes, List.of(), element.text());
    }
    if (element.children().isEmpty()) {
      return element;
    }
    return new Element(
        element.name(),
        element.attributes(),
        element.children().stream().map(child -> linked(child, address)).toList(),
        element.text());
  }
}
