// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The point-in-time view: a version's text, the versions either side converted ahead; on 2015/1998
 * with its original text and one consolidated version, of 22 March 2026. The work runs on the
 * calling thread here, so each future is complete when returned; the conversions ahead are kept in
 * {@code ahead}, to run when a test says.
 */
class PointInTimeTest {
  final RecordedExpressions expressions = new RecordedExpressions();
  final List<Runnable> ahead = new ArrayList<>();
  final PointInTime pointInTime =
      new PointInTime(expressions, new PreConversion(expressions, ahead::add), Runnable::run);

  @Test
  void givesTheTextOfTheVersion() {
    assertThat(pointInTime.expression(ACT, CONSOLIDATED, Language.ENG).join())
        .hasValueSatisfying(
            text -> assertThat(text.source().celex()).isEqualTo("02015R1998-20260322"));
    assertThat(pointInTime.expression(ACT, ORIGINAL, Language.ENG).join())
        .hasValueSatisfying(text -> assertThat(text.source().celex()).isEqualTo("32015R1998"));
    assertThat(expressions.asked)
        .containsExactly(
            ExpressionIdentifier.of(CONSOLIDATED, Language.ENG),
            ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void convertsTheVersionsEitherSideAheadOnceTheTextIsGiven() {
    pointInTime.expression(ACT, CONSOLIDATED, Language.ENG).join();
    expressions.asked.clear();

    ahead.forEach(Runnable::run);

    assertThat(expressions.asked).containsExactly(ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void hasNoTextInALanguageTheVersionHasNoneAndConvertsNothingAhead() {
    assertThat(pointInTime.expression(ACT, CONSOLIDATED, Language.GLE).join()).isEmpty();
    assertThat(ahead).isEmpty();
  }

  @Test
  void refusesAVersionOfAnotherAct() {
    var other =
        Version.original(
            LocalDate.of(2010, 4, 29),
            new ActIdentifier("32010R0185"),
            EnumSet.allOf(Language.class),
            Instant.parse("2026-07-10T01:02:19.329Z"));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> pointInTime.expression(ACT, other, Language.ENG));
    assertThat(expressions.asked).isEmpty();
  }
}
