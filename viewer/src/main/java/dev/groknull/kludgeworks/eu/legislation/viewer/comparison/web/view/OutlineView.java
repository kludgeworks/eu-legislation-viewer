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
 * The later text's outline as a comparison shows it (Design: Outline, sidebar): every branch
 * leading to a difference with a bar and a count, open, down to the unit in the outline the
 * difference is in, its section (8.1.2 Screening); a unit with differences in it below it listing
 * them as chips, by their nums and their parent's where that isn't it (8.1.2.3 (c)), each a jump to
 * it. A difference in no section (a recital) is in none of its branches.
 *
 * @param entries the outermost units
 */
public record OutlineView(List<Branch> entries) {

  public OutlineView {
    entries = List.copyOf(entries);
  }

  /** {@code outline} with {@code differences} counted, numbered from 1 as the Changes view has. */
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
      var chips = new ArrayList<Chip>();
      for (var numbered : own) {
        if (!numbered.difference().eId().equals(entry.eId())) {
          chips.add(new Chip(chip(numbered.difference(), entry.eId()), numbered.number()));
        }
      }
      var label = (entry.num() + " " + entry.heading()).strip();
      branches.add(
          new Branch(
              label.isEmpty() ? entry.kind() : label,
              count,
              total == 0 ? 0 : count * 100 / total,
              first,
              chips,
              below));
    }
    return branches;
  }

  /**
   * What a chip says of {@code difference} in the section {@code section}: its num, after its
   * parent's where its parent isn't the section (8.1.2.3 (c)); empty if it has no num.
   */
  private static String chip(Difference difference, String section) {
    var num = num(difference.shown());
    var lead = difference.lead();
    if (lead == null || section.equals(difference.parent())) {
      return num;
    }
    return (num(lead) + " " + num).strip();
  }

  /** {@code unit}'s num, as the text has it: (c); empty if it has none. */
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
   * @param label its num and heading: 8.1.2 Screening
   * @param count how many differences are in it; 0 if none
   * @param bar its share of the comparison's differences, in percent
   * @param first the number of the first difference in it, which it jumps to; 0 if none
   * @param chips the differences in its points, each a jump
   * @param entries the units below it
   */
  public record Branch(
      String label, int count, int bar, int first, List<Chip> chips, List<Branch> entries) {

    public Branch {
      chips = List.copyOf(chips);
      entries = List.copyOf(entries);
    }

    /** Where it jumps to: its first difference; none if it has none. */
    public @Nullable String href() {
      return first == 0 ? null : "#difference-" + first;
    }

    /** Its bar's width, as its style has it. */
    public String barStyle() {
      return "width: " + bar + "%";
    }
  }

  /**
   * A difference in a unit's points.
   *
   * @param label the point's num, after its parent's where that isn't the unit: 8.1.2.3 (c)
   * @param number the difference's number, which it jumps to
   */
  public record Chip(String label, int number) {}
}
