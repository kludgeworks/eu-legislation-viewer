// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment.Role;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmendmentTest {

  @ParameterizedTest
  @CsvSource({
    "M, AMENDMENT",
    "R, REPLACEMENT",
    "R/PART, REPLACEMENT",
    "J, ADDITION",
    "C, ADDITION",
    "DEL, DELETION",
    "SU, DELETION",
    "A, REPEAL",
    "AP, REPEAL",
    "P, EXTENSION",
    "P/PART, EXTENSION",
    "PI, EXTENSION",
    "S, SUSPENSION",
    "SP, SUSPENSION",
    "T, DEFERRAL"
  })
  void mapsCellarCodesToRoles(String code, Role expected) {
    assertThat(Role.of(code)).contains(expected);
  }

  @Test
  void unrecognisedCellarCodeYieldsEmpty() {
    assertThat(Role.of("UNKNOWN")).isEmpty();
  }

  @Test
  void amendmentRoleChangesWording() {
    assertThat(Role.AMENDMENT.wording()).isTrue();
    assertThat(Role.AMENDMENT.removes()).isFalse();

    var amendment =
        new Amendment(
            new ActIdentifier("32026R0449"),
            new Location("AR 1"),
            Role.AMENDMENT,
            null,
            null,
            Set.of(Language.ENG));

    assertThat(amendment.changes(Language.ENG)).isTrue();
    assertThat(amendment.changes(Language.FRA)).isFalse();
  }
}
