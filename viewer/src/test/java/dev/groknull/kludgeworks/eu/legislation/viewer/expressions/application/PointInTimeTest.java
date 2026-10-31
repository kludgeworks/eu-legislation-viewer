// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.AVSEC;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The point-in-time view: the version acts chooses for the date, and that version's text; on
 * 2015/1998 with its original text and one consolidated version, of 22 March 2026. The work runs on
 * the calling thread here, so each future is complete when returned; the conversions ahead are kept
 * in {@code ahead}, to run when a test says.
 */
class PointInTimeTest {
  final RecordedExpressions expressions = new RecordedExpressions();
  final List<Runnable> ahead = new ArrayList<>();
  final PointInTime pointInTime =
      new PointInTime(
          new OneAct(), expressions, new PreConversion(expressions, ahead::add), Runnable::run);

  @Test
  void givesTheTextOfTheVersionThatAppliesOnTheDate() {
    assertThat(pointInTime.expression(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).join())
        .hasValueSatisfying(
            text -> assertThat(text.source().celex()).isEqualTo("02015R1998-20260322"));
    assertThat(pointInTime.expression(AVSEC, LocalDate.of(2026, 3, 21), Language.ENG).join())
        .hasValueSatisfying(text -> assertThat(text.source().celex()).isEqualTo("32015R1998"));
    assertThat(expressions.asked)
        .containsExactly(
            ExpressionIdentifier.of(CONSOLIDATED, Language.ENG),
            ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void convertsTheVersionsEitherSideAheadOnceTheTextIsGiven() {
    pointInTime.expression(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).join();
    expressions.asked.clear();

    ahead.forEach(Runnable::run);

    assertThat(expressions.asked).containsExactly(ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void convertsNothingAheadWithoutAText() {
    pointInTime.expression(AVSEC, LocalDate.of(2026, 4, 1), Language.GLE).join();
    pointInTime.expression(AVSEC, LocalDate.of(2015, 1, 1), Language.ENG).join();

    assertThat(ahead).isEmpty();
  }

  @Test
  void hasNoTextBeforeTheActsTextAppliesOrForAnotherAct() {
    assertThat(pointInTime.expression(AVSEC, LocalDate.of(2015, 1, 1), Language.ENG).join())
        .isEmpty();
    assertThat(
            pointInTime
                .expression(new ActIdentifier("32010R0185"), LocalDate.of(2026, 4, 1), Language.ENG)
                .join())
        .isEmpty();
    assertThat(expressions.asked).isEmpty();
  }

  @Test
  void hasNoTextInALanguageTheVersionHasNone() {
    assertThat(pointInTime.expression(AVSEC, LocalDate.of(2026, 4, 1), Language.GLE).join())
        .isEmpty();
  }
}
