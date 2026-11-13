// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The changes that Cellar reports, mapped to the acts that the viewer holds: by the work itself, or
 * by the act that it is based on, amends or corrects. The examples are changes seen on 2026-11-10
 * and 2026-11-12 (the spike on how to learn of changes in Cellar).
 */
class CellarChangeTest {
  private static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  private static final ActIdentifier VAT = new ActIdentifier("32007R0862");
  private static final Instant MORNING = Instant.parse("2026-11-12T08:38:00Z");
  private static final Instant NOON = Instant.parse("2026-11-12T11:55:04Z");

  /** The held acts by each CELEX they are known by: the act, and each version it has. */
  private static final Map<String, ActIdentifier> HELD =
      Map.of(
          "32015R1998", AVSEC,
          "02015R1998-20260322", AVSEC,
          "32007R0862", VAT);

  @Test
  void aNewConsolidationTouchesTheActItIsBasedOn() {
    var touched =
        CellarChange.byHeldAct(
            List.of(new CellarChange("02007R0862-20280101", Set.of("32007R0862"), MORNING)), HELD);

    assertThat(touched).containsExactly(Map.entry(VAT, MORNING));
  }

  @Test
  void anAmendingActOrCorrigendumTouchesTheActsItNames() {
    var touched =
        CellarChange.byHeldAct(
            List.of(
                new CellarChange("32026R1972", Set.of("32019R1021", "32015R1998"), MORNING),
                new CellarChange("32026R1127R(01)", Set.of("32007R0862"), NOON)),
            HELD);

    assertThat(touched).containsOnly(Map.entry(AVSEC, MORNING), Map.entry(VAT, NOON));
  }

  @Test
  void aRepublishedVersionTouchesItsActWithItsLatestChange() {
    var touched =
        CellarChange.byHeldAct(
            List.of(
                new CellarChange("02015R1998-20260322", Set.of("32015R1998"), NOON),
                new CellarChange("32015R1998", Set.of(), MORNING)),
            HELD);

    assertThat(touched).containsExactly(Map.entry(AVSEC, NOON));
  }

  @Test
  void aChangeOfAnActThatIsNotHeldIsLeftOut() {
    var touched =
        CellarChange.byHeldAct(
            List.of(
                new CellarChange("62025TN0321", Set.of(), MORNING),
                new CellarChange("02022R2105-20261024", Set.of("32022R2105"), NOON)),
            HELD);

    assertThat(touched).isEmpty();
  }

  @Test
  void theCelexesToLookUpAreTheWorkAndItsTargets() {
    assertThat(new CellarChange("32026R1972", Set.of("32019R1021"), MORNING).celexes())
        .containsExactlyInAnyOrder("32026R1972", "32019R1021");
  }
}
