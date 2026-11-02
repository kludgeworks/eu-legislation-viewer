// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class LanguageTest {

  @Test
  void hasTheEuCodeAndTheIsoCode() {
    assertThat(Language.ENG.code()).isEqualTo("ENG");
    assertThat(Language.ENG.iso()).isEqualTo("en");
    assertThat(Language.GLE.iso()).isEqualTo("ga");
  }

  @Test
  void isFoundByItsIsoCode() {
    assertThat(Language.ofIso("en")).isEqualTo(Language.ENG);
    assertThat(Language.ofIso("SV")).isEqualTo(Language.SWE);
    assertThatIllegalArgumentException().isThrownBy(() -> Language.ofIso("no"));
  }

  @Test
  void isFoundByItsEuCode() {
    assertThat(Language.ofCode("eng")).isEqualTo(Language.ENG);
    assertThat(Language.ofCode("GLE")).isEqualTo(Language.GLE);
    assertThatIllegalArgumentException().isThrownBy(() -> Language.ofCode("en"));
  }

  @Test
  void hasTheTwentyFourOfficialLanguages() {
    assertThat(Language.values()).hasSize(24);
  }
}
