// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.build.rules.KnownBugs;
import org.junit.jupiter.api.Test;

/**
 * Runs the comparison's examples tagged {@code @known-bug}, and checks that each still fails
 * ({@link KnownBugs}). The rules run leaves them out (ComparisonRules), so it stays green.
 */
class ComparisonKnownBugs {
  @Test
  void eachKnownBugStillFails() {
    var result =
        KnownBugs.run(
            "rules.comparison", "dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model");

    result.failing().forEach(System.out::println);
    assertThat(result.problems()).as("the known bugs").isEmpty();
    System.out.printf("%d known bugs, each still failing%n", result.count());
  }
}
