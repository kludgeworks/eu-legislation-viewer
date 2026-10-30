// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.application;

import static dev.groknull.kludgeworks.eu.legislation.viewer.text.application.OneAct.AVSEC;
import static dev.groknull.kludgeworks.eu.legislation.viewer.text.application.OneAct.CONSOLIDATED;
import static dev.groknull.kludgeworks.eu.legislation.viewer.text.application.OneAct.ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActVersionsChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTextIdentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** An act's versions converted ahead when they change: on 2015/1998 and its two versions. */
class PreConversionTest {
  final RecordedTexts texts = new RecordedTexts();
  final PreConversion preConversion =
      new PreConversion(
          new OneAct(), texts, new PreConversion.Properties(List.of(Language.ENG, Language.FRA)));

  @Test
  void convertsEveryVersionInEachLanguage() {
    preConversion.on(new ActVersionsChanged(AVSEC));

    assertThat(texts.asked)
        .containsExactly(
            LegalTextIdentifier.of(ORIGINAL, Language.ENG),
            LegalTextIdentifier.of(ORIGINAL, Language.FRA),
            LegalTextIdentifier.of(CONSOLIDATED, Language.ENG),
            LegalTextIdentifier.of(CONSOLIDATED, Language.FRA));
  }

  @Test
  void convertsTheOtherVersionsWhenOneFails() {
    texts.failing.add(ORIGINAL.celex());

    preConversion.on(new ActVersionsChanged(AVSEC));

    assertThat(texts.asked).contains(LegalTextIdentifier.of(CONSOLIDATED, Language.ENG));
  }

  @Test
  void convertsNothingForAnActCellarDoesNotKnow() {
    preConversion.on(new ActVersionsChanged(new ActIdentifier("32010R0185")));

    assertThat(texts.asked).isEmpty();
  }
}
