// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ResourceTypeTest {

  @Test
  void isTheCodeOfItsUriInTheAuthorityTable() {
    assertThat(
            ResourceType.of(
                "http://publications.europa.eu/resource/authority/resource-type/REG_IMPL"))
        .isEqualTo(new ResourceType("REG_IMPL"));
  }

  @Test
  void refusesAnotherTablesUriOrACodeOfAnotherForm() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> ResourceType.of("http://publications.europa.eu/resource/authority/language/ENG"));
    assertThatIllegalArgumentException().isThrownBy(() -> new ResourceType("reg_impl"));
  }
}
