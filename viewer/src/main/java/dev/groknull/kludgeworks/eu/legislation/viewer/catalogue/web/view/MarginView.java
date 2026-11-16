// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The marks in the margin of the text on the Read page, where the text is not the law on the day
 * asked. The Reading card has the warning. The margin shows where.
 *
 * <ul>
 *   <li>A dashed amber rule beside each unit that a divergence touches, {@link Divergence#in}, and
 *       a small link up to the warning: "2017/815 not included ↑".
 *   <li>For a place that the text does not have, the rule is beside the heading of the unit that
 *       holds it.
 *   <li>For the early text of the act itself, the whole text, and a note at the top: "Does not
 *       apply on this day. Applies from 25 May 2018 · Article 99(2) of 2016/679."
 * </ul>
 *
 * <p>The content module renders the text without the act. So the page sends the marks, and
 * js/margin.js puts them on the text when it arrives.
 *
 * @param rules the units to mark, in the order of the text
 * @param note the note at the top of a text that does not apply at all on the day, after "Does not
 *     apply on this day.": from when it applies. For example, "Applies from 25 May 2018 · Article
 *     99(2) of 2016/679.". Null if the text applies.
 */
public record MarginView(List<Rule> rules, @Nullable String note) {

  public MarginView {
    rules = List.copyOf(rules);
  }

  /** Returns the view with no marks. */
  public static MarginView none() {
    return new MarginView(List.of(), null);
  }

  /**
   * Returns the marks of the text of {@code version} of {@code act}, for the day {@code asked}.
   *
   * @param marks the marks of the built texts in {@code language}, by version. Without the text of
   *     {@code version}, there are no rules.
   * @param today the date today, which tells "applied" from "applies" in the note
   */
  public static MarginView of(
      Act act,
      Version version,
      LocalDate asked,
      Language language,
      Map<Version, TextMarks> marks,
      LocalDate today) {
    var text = marks.get(version);
    var found = new LinkedHashMap<String, Rule>();
    String note = null;
    for (var divergence : act.divergences(asked, language, marks)) {
      if (divergence.whole(act.id())) {
        note = DivergenceView.of(act, divergence, version, asked, language, today).detail();
        continue;
      }
      if (text == null) {
        continue;
      }
      var label = label(act, divergence);
      for (var unit : divergence.in(text.eIds())) {
        found.merge(unit.eId(), new Rule(unit.eId(), unit.holds(), label), MarginView::merge);
      }
    }
    var eIds = text == null ? List.<String>of() : text.eIds();
    var rules =
        found.values().stream()
            .sorted(Comparator.comparingInt(rule -> eIds.indexOf(rule.unit())))
            .toList();
    return new MarginView(rules, note);
  }

  /**
   * Returns one rule for a unit that two divergences touch. It marks the whole unit if either does,
   * and names each act once.
   */
  private static Rule merge(Rule one, Rule other) {
    var labels = new ArrayList<>(List.of(one.label().split("; ")));
    if (!labels.contains(other.label())) {
      labels.add(other.label());
    }
    return new Rule(one.unit(), one.heading() && other.heading(), String.join("; ", labels));
  }

  /** Returns the words of the link beside a unit. For example, "2017/815 not included". */
  private static String label(Act act, Divergence divergence) {
    var cited = act.cited(divergence.act());
    return switch (divergence.kind()) {
      case LATE, UNCONFIRMED -> cited + " not included";
      case EARLY -> cited + " does not apply yet";
    };
  }

  /**
   * A unit to mark.
   *
   * @param unit the eId of the unit
   * @param heading whether to mark only its heading, because it holds a place that the text does
   *     not have
   * @param label the words of the link up to the warning
   */
  public record Rule(String unit, boolean heading, String label) {}
}
