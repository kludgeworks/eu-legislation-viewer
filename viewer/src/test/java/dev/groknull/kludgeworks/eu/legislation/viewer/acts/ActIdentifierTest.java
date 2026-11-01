// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CELEX numbers as Cellar has them (EUR-Lex, How CELEX numbers are composed). */
class ActIdentifierTest {

  @Test
  void takesAnyActsCelexNumber() {
    assertThat(ActIdentifier.isCelex("32015R1998")).isTrue();
    assertThat(ActIdentifier.isCelex("32015R1998R(01)")).isTrue();
    assertThat(ActIdentifier.isCelex("12003T/ACT")).isTrue();
    assertThat(ActIdentifier.isCelex("C2021/522A/01")).isTrue();
    assertThat(ActIdentifier.isCelex("C/2025/03118")).isTrue();
    // malformed in Cellar's own data: whether an act has it is Cellar's to say
    assertThat(ActIdentifier.isCelex("1201994N/ACT")).isTrue();
  }

  @Test
  void refusesWhatCantBeOne() {
    assertThat(ActIdentifier.isCelex("")).isFalse();
    assertThat(ActIdentifier.isCelex("32015R1998 x")).isFalse();
    assertThat(ActIdentifier.isCelex("02015R1998-20260322")).isFalse();
  }
}
