// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.AVSEC;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.LegalTextIdentifier;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The point-in-time view: the version acts chooses for the date, and that version's text; on
 * 2015/1998 with its original text and one consolidated version, of 22 March 2026. The work runs on
 * the calling thread here, so each future is complete when returned.
 */
class PointInTimeTest {
  final RecordedTexts texts = new RecordedTexts();
  final PointInTime pointInTime = new PointInTime(new OneAct(), texts, Runnable::run);

  @Test
  void givesTheTextOfTheVersionThatAppliesOnTheDate() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).join())
        .hasValueSatisfying(
            text -> assertThat(text.source().celex()).isEqualTo("02015R1998-20260322"));
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 3, 21), Language.ENG).join())
        .hasValueSatisfying(text -> assertThat(text.source().celex()).isEqualTo("32015R1998"));
    assertThat(texts.asked)
        .containsExactly(
            LegalTextIdentifier.of(CONSOLIDATED, Language.ENG),
            LegalTextIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void hasNoTextBeforeTheActsTextAppliesOrForAnotherAct() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2015, 1, 1), Language.ENG).join()).isEmpty();
    assertThat(
            pointInTime
                .text(new ActIdentifier("32010R0185"), LocalDate.of(2026, 4, 1), Language.ENG)
                .join())
        .isEmpty();
    assertThat(texts.asked).isEmpty();
  }

  @Test
  void hasNoTextInALanguageTheVersionHasNone() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 4, 1), Language.GLE).join()).isEmpty();
  }
}
