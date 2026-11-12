// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Wording;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The outline of the later text, as the sidebar of a comparison shows it.
 *
 * <ul>
 *   <li>Each branch that leads to a difference has a bar and a count, and is open. The branch goes
 *       down to the unit in the outline that the difference is in, which is its section. For
 *       example, "8.1.2 Screening".
 *   <li>A unit with differences in its points lists them as rows, one level below it. A row shows
 *       the difference's own num, for example "(c)", never prefixed with "ANNEX", and the number of
 *       the difference. Each row jumps to its difference. A unit with more than {@value #ROWS} such
 *       differences lists none: its count is enough.
 *   <li>A difference in no section, for example a recital, is in none of the branches.
 * </ul>
 *
 * @param entries the outermost units
 */
public record OutlineView(List<Branch> entries) {

  /** The most rows a unit lists. A unit with more differences in its points lists none. */
  static final int ROWS = 12;

  public OutlineView {
    entries = List.copyOf(entries);
  }

  /**
   * Returns {@code outline} with {@code differences} counted. The differences are numbered from 1,
   * as in the Changes view.
   */
  public static OutlineView of(Outline outline, List<Difference> differences) {
    var bySection = new HashMap<String, List<Numbered>>();
    for (var i = 0; i < differences.size(); i++) {
      var difference = differences.get(i);
      if (difference.section() != null) {
        bySection
            .computeIfAbsent(difference.section(), section -> new ArrayList<>())
            .add(new Numbered(i + 1, difference));
      }
    }
    return new OutlineView(branches(outline.entries(), bySection, differences.size()));
  }

  private static List<Branch> branches(
      List<Outline.Entry> entries, Map<String, List<Numbered>> bySection, int total) {
    var branches = new ArrayList<Branch>();
    for (var entry : entries) {
      var below = branches(entry.entries(), bySection, total);
      var own = bySection.getOrDefault(entry.eId(), List.of());
      var count = own.size() + below.stream().mapToInt(Branch::count).sum();
      var first =
          Stream.concat(own.stream().map(Numbered::number), below.stream().map(Branch::first))
              .filter(number -> number > 0)
              .min(Integer::compare)
              .orElse(0);
      var rows = new ArrayList<Row>();
      for (var numbered : own) {
        if (!numbered.difference().eId().equals(entry.eId())) {
          rows.add(new Row(row(numbered.difference()), numbered.number()));
        }
      }
      var label = (entry.num() + " " + entry.heading()).strip();
      branches.add(
          new Branch(
              label.isEmpty() ? entry.kind() : label,
              count,
              total == 0 ? 0 : count * 100 / total,
              first,
              listed(rows),
              below));
    }
    return branches;
  }

  /** Returns {@code rows}, or none if there are more than {@value #ROWS}. */
  static List<Row> listed(List<Row> rows) {
    return rows.size() > ROWS ? List.of() : rows;
  }

  /**
   * Returns the label of the row of {@code difference}: its own num, for example "(c)". Without a
   * num, it is the num of the unit it opens with, if any. Empty if neither has one.
   */
  private static String row(Difference difference) {
    var num = num(difference.shown());
    var lead = difference.lead();
    return num.isEmpty() && lead != null ? num(lead) : num;
  }

  /**
   * Returns the num of {@code unit} as the text has it, for example "(c)". Empty if it has none.
   */
  private static String num(Element unit) {
    return unit.children().stream()
        .filter(child -> child.name().equals("num"))
        .map(Wording::of)
        .findFirst()
        .orElse("");
  }

  private record Numbered(int number, Difference difference) {}

  /**
   * A unit in the outline.
   *
   * @param label its num and heading, for example "8.1.2 Screening"
   * @param count how many differences are in it. 0 if none.
   * @param bar its share of the differences of the comparison, in percent
   * @param first the number of the first difference in it, which it jumps to. 0 if none.
   * @param rows the differences in its points, one row each. Each row jumps to its difference. None
   *     if there are more than {@value OutlineView#ROWS}.
   * @param entries the units below it
   */
  public record Branch(
      String label, int count, int bar, int first, List<Row> rows, List<Branch> entries) {

    public Branch {
      rows = List.copyOf(rows);
      entries = List.copyOf(entries);
    }

    /** Returns the link to its first difference. Returns null if it has no differences. */
    public @Nullable String href() {
      return first == 0 ? null : "#difference-" + first;
    }

    /** Returns the width of its bar, as a CSS style. */
    public String barStyle() {
      return "width: " + bar + "%";
    }
  }

  /**
   * A difference in the points of a unit, as a row of the outline.
   *
   * @param label the difference's own num. For example, "(c)".
   * @param number the number of the difference, which the row jumps to
   */
  public record Row(String label, int number) {}
}
