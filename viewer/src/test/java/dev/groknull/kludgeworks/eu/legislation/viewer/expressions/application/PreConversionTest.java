// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ACT;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import org.junit.jupiter.api.Test;

/**
 * The versions either side of the one read, converted ahead: on 2015/1998 and its two versions. The
 * work runs on the calling thread here.
 */
class PreConversionTest {
  final RecordedExpressions expressions = new RecordedExpressions();
  final PreConversion preConversion = new PreConversion(expressions, Runnable::run);

  @Test
  void convertsTheNextVersionInTheReadersLanguage() {
    preConversion.around(ACT, ORIGINAL, Language.FRA);

    assertThat(expressions.asked)
        .containsExactly(ExpressionIdentifier.of(CONSOLIDATED, Language.FRA));
  }

  @Test
  void convertsThePreviousVersion() {
    preConversion.around(ACT, CONSOLIDATED, Language.ENG);

    assertThat(expressions.asked).containsExactly(ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void logsAVersionThatFails() {
    expressions.failing.add(ORIGINAL.celex());

    assertThatNoException().isThrownBy(() -> preConversion.around(ACT, CONSOLIDATED, Language.ENG));
    assertThat(expressions.asked).containsExactly(ExpressionIdentifier.of(ORIGINAL, Language.ENG));
  }
}
