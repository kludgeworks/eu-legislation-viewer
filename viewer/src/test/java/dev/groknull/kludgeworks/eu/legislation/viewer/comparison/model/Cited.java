// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Reads acts as the rules cite them: a regulation by its number, a corrigendum with its act. */
final class Cited {
  /** The number of a regulation. For example, 2015/1998. */
  private static final Pattern REGULATION = Pattern.compile("(\\d{4})/(\\d+)");

  /** A corrigendum, as the pages cite it. For example, "Corrigendum 1 to 2017/815". */
  private static final Pattern CORRIGENDUM = Pattern.compile("Corrigendum (\\d+) to (\\d{4}/\\d+)");

  /** The words for a difference that no known act made. */
  static final String NO_KNOWN_ACT = "no known act";

  private Cited() {}

  /** Returns the act cited as {@code cited}: "2015/1998" or "Corrigendum 1 to 2017/815". */
  static ActIdentifier act(String cited) {
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
    return new ActIdentifier(
        "3" + regulation.group(1) + "R" + "%04d".formatted(Integer.parseInt(regulation.group(2))));
  }

  /**
   * Returns the acts cited as {@code cited}, joined by "and": "2019/103 and 2026/449". Returns none
   * for {@link #NO_KNOWN_ACT}.
   */
  static List<ActIdentifier> acts(String cited) {
    if (cited.equals(NO_KNOWN_ACT)) {
      return List.of();
    }
    return Arrays.stream(cited.split(" and ")).map(Cited::act).toList();
  }
}
