// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads acts as the rules cite them: a regulation by its number, a corrigendum with its act. The
 * steps of the rules of each module read acts so.
 */
public final class Cited {
  /**
   * The number of a regulation, as the Official Journal cites it: the year first from 2015, for
   * example 2015/1998, and the number first before 2015, for example 917/2004.
   */
  private static final Pattern REGULATION = Pattern.compile("(\\d{1,4})/(\\d{1,4})");

  /** The year from which a regulation's number starts with its year. */
  private static final int YEAR_FIRST = 2015;

  /** A corrigendum, as the pages cite it. For example, "Corrigendum 1 to 2017/815". */
  private static final Pattern CORRIGENDUM = Pattern.compile("Corrigendum (\\d+) to (\\d{4}/\\d+)");

  /** A directive, by its number. For example, "Directive 2019/878". */
  private static final Pattern DIRECTIVE = Pattern.compile("Directive (\\d{4})/(\\d+)");

  /** A decision, by its number. For example, "Decision 2022/2319". */
  private static final Pattern DECISION = Pattern.compile("Decision (\\d{4})/(\\d+)");

  /** The words for a difference that no known act made. */
  public static final String NO_KNOWN_ACT = "no known act";

  private Cited() {}

  /**
   * Returns the act cited as {@code cited}: "2015/1998", "917/2004", "Directive 2002/73", "Decision
   * 2022/2319" or "Corrigendum 1 to 2017/815".
   */
  public static ActIdentifier act(String cited) {
    var decision = DECISION.matcher(cited);
    if (decision.matches()) {
      return new ActIdentifier(
          "3" + decision.group(1) + "D" + "%04d".formatted(Integer.parseInt(decision.group(2))));
    }
    var directive = DIRECTIVE.matcher(cited);
    if (directive.matches()) {
      return new ActIdentifier(
          "3" + directive.group(1) + "L" + "%04d".formatted(Integer.parseInt(directive.group(2))));
    }
    var corrigendum = CORRIGENDUM.matcher(cited);
    if (corrigendum.matches()) {
      return new ActIdentifier(
          act(corrigendum.group(2)).celex()
              + "R(%02d)".formatted(Integer.parseInt(corrigendum.group(1))));
    }
    var regulation = REGULATION.matcher(cited);
    if (!regulation.matches()) {
      throw new IllegalArgumentException("Not an act as the rules cite one: " + cited);
    }
    var first = Integer.parseInt(regulation.group(1));
    var second = Integer.parseInt(regulation.group(2));
    var yearFirst = first >= YEAR_FIRST;
    return new ActIdentifier(
        "3" + (yearFirst ? first : second) + "R" + "%04d".formatted(yearFirst ? second : first));
  }

  /**
   * Returns the acts cited as {@code cited}, as the pages list them: "2019/103 and 2026/449",
   * "2017/815, 2019/103 and 2019/1583". Returns none for {@link #NO_KNOWN_ACT}.
   */
  public static List<ActIdentifier> acts(String cited) {
    if (cited.equals(NO_KNOWN_ACT)) {
      return List.of();
    }
    return Arrays.stream(cited.split(", | and ")).map(Cited::act).toList();
  }
}
