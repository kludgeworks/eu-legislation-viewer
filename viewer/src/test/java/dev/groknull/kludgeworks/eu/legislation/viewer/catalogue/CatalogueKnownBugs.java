// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.build.rules.KnownBugs;
import org.junit.jupiter.api.Test;

/**
 * Runs the catalogue's examples tagged {@code @known-bug}, and checks that each still fails ({@link
 * KnownBugs}). The rules run leaves them out (CatalogueRules), so it stays green.
 */
class CatalogueKnownBugs {
  @Test
  void eachKnownBugStillFails() {
    var result =
        KnownBugs.run(
            "rules.catalogue", "dev.groknull.kludgeworks.eu.legislation.viewer.catalogue");

    result.failing().forEach(System.out::println);
    assertThat(result.problems()).as("the known bugs").isEmpty();
    System.out.printf("%d known bugs, each still failing%n", result.count());
  }
}
