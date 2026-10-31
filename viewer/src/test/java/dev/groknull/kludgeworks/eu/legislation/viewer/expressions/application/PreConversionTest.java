// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.AVSEC;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActVersionsChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An act's versions converted ahead when they change: on 2015/1998 and its two versions. */
class PreConversionTest {
  final RecordedExpressions expressions = new RecordedExpressions();
  final PreConversion preConversion =
      new PreConversion(
          new OneAct(),
          expressions,
          new PreConversion.Properties(List.of(Language.ENG, Language.FRA)));

  @Test
  void convertsEveryVersionInEachLanguage() {
    preConversion.on(new ActVersionsChanged(AVSEC));

    assertThat(expressions.asked)
        .containsExactly(
            ExpressionIdentifier.of(ORIGINAL, Language.ENG),
            ExpressionIdentifier.of(ORIGINAL, Language.FRA),
            ExpressionIdentifier.of(CONSOLIDATED, Language.ENG),
            ExpressionIdentifier.of(CONSOLIDATED, Language.FRA));
  }

  @Test
  void convertsTheOtherVersionsWhenOneFails() {
    expressions.failing.add(ORIGINAL.celex());

    preConversion.on(new ActVersionsChanged(AVSEC));

    assertThat(expressions.asked).contains(ExpressionIdentifier.of(CONSOLIDATED, Language.ENG));
  }

  @Test
  void convertsNothingForAnActCellarDoesNotKnow() {
    preConversion.on(new ActVersionsChanged(new ActIdentifier("32010R0185")));

    assertThat(expressions.asked).isEmpty();
  }
}
