// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests the deferred application of an act: the act is in force, but it does not apply in full on
 * the day yet. The acts have the dates of 2016/679, 2018/1862 and an act that applies from its
 * entry into force.
 */
class DeferredApplicationTest {
  private static final LocalDate GDPR_IN_FULL = LocalDate.of(2018, 5, 25);
  private static final LocalDate SIS_IN_FULL = LocalDate.of(2023, 3, 7);

  /** 2016/679: in force on 24 May 2016, applies from 25 May 2018. */
  private static final Act GDPR =
      act(
          "32016R0679",
          LocalDate.of(2016, 5, 4),
          date(LocalDate.of(2016, 5, 24), ActDate.Kind.ENTRY_INTO_FORCE, false),
          date(GDPR_IN_FULL, ActDate.Kind.APPLICATION, false));

  /** 2018/1862: applies in part from 28 December 2018, and in full from 7 March 2023. */
  private static final Act SIS =
      act(
          "32018R1862",
          LocalDate.of(2018, 12, 27),
          date(LocalDate.of(2018, 12, 27), ActDate.Kind.ENTRY_INTO_FORCE, false),
          date(LocalDate.of(2018, 12, 28), ActDate.Kind.APPLICATION, true),
          date(SIS_IN_FULL, ActDate.Kind.APPLICATION, false));

  /** Applies from its entry into force, the day of its first version. */
  private static final Act AT_ONCE =
      act(
          "32019R0818",
          LocalDate.of(2019, 6, 11),
          date(LocalDate.of(2019, 6, 11), ActDate.Kind.ENTRY_INTO_FORCE, false));

  @Test
  void anActInForceThatDoesNotApplyYetHasItsApplicationDeferredInFull() {
    var deferred = LawOnADay.of(GDPR).deferredApplicationOn(LocalDate.of(2017, 3, 1), Language.ENG);

    assertThat(deferred).isPresent();
    assertThat(deferred.get().day()).isEqualTo(GDPR_IN_FULL);
    assertThat(deferred.get().partial()).isFalse();
  }

  @Test
  void anActHasNoDeferredApplicationFromTheDayItAppliesInFull() {
    assertThat(LawOnADay.of(GDPR).deferredApplicationOn(GDPR_IN_FULL, Language.ENG)).isEmpty();
    assertThat(LawOnADay.of(GDPR).deferredApplicationOn(GDPR_IN_FULL.minusDays(1), Language.ENG))
        .isPresent();
  }

  @Test
  void anActThatAppliesInPartHasAPartialDeferredApplication() {
    var deferred = LawOnADay.of(SIS).deferredApplicationOn(LocalDate.of(2022, 8, 1), Language.ENG);

    assertThat(deferred).isPresent();
    assertThat(deferred.get().day()).isEqualTo(SIS_IN_FULL);
    assertThat(deferred.get().partial()).isTrue();
    // On the day of its first version, no part applies yet.
    assertThat(LawOnADay.of(SIS).deferredApplicationOn(LocalDate.of(2018, 12, 27), Language.ENG))
        .hasValueSatisfying(first -> assertThat(first.partial()).isFalse());
  }

  @Test
  void anActThatAppliesFromItsEntryIntoForceHasNoDeferredApplication() {
    assertThat(LawOnADay.of(AT_ONCE).deferredApplicationOn(LocalDate.of(2019, 6, 11), Language.ENG))
        .isEmpty();
    assertThat(LawOnADay.of(AT_ONCE).deferredApplicationOn(LocalDate.of(2025, 1, 1), Language.ENG))
        .isEmpty();
  }

  @Test
  void aDayBeforeTheFirstVersionHasNoDeferredApplication() {
    assertThat(LawOnADay.of(GDPR).deferredApplicationOn(LocalDate.of(2016, 5, 3), Language.ENG))
        .isEmpty();
  }

  private static Act act(String celex, LocalDate original, ActDate... dates) {
    var id = new ActIdentifier(celex);
    return new Act(
        id,
        null,
        new ResourceType("REG"),
        Map.of(),
        null,
        true,
        null,
        new Timeline(
            List.of(Version.original(original, id, EnumSet.allOf(Language.class), Map.of()))),
        new ActDates(id, List.of(dates), Map.of()),
        List.of(),
        Map.of());
  }

  private static ActDate date(@Nullable LocalDate day, ActDate.Kind kind, boolean partial) {
    return new ActDate(
        day, kind, partial, new ActDate.Clause(null, null, List.of(), List.of(), List.of(), ""));
  }
}
