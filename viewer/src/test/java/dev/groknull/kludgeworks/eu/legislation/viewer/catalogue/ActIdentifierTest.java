// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests CELEX numbers as Cellar has them. The rules come from the EUR-Lex page "How CELEX numbers
 * are composed".
 */
class ActIdentifierTest {

  @Test
  void takesAnyActsCelexNumber() {
    assertThat(ActIdentifier.isCelex("32015R1998")).isTrue();
    assertThat(ActIdentifier.isCelex("32015R1998R(01)")).isTrue();
    assertThat(ActIdentifier.isCelex("12003T/ACT")).isTrue();
    assertThat(ActIdentifier.isCelex("C2021/522A/01")).isTrue();
    assertThat(ActIdentifier.isCelex("C/2025/03118")).isTrue();
    // This number is malformed in Cellar's own data. Cellar, not this check, says if an act has it.
    assertThat(ActIdentifier.isCelex("1201994N/ACT")).isTrue();
  }

  @Test
  void numbersAnActAsTheOfficialJournalCitesIt() {
    assertThat(new ActIdentifier("32015R1998").number()).contains("2015/1998");
    assertThat(new ActIdentifier("32019R0103").number()).contains("2019/103");
    assertThat(new ActIdentifier("32010R0185").number()).contains("185/2010");
    assertThat(new ActIdentifier("32008L0050").number()).contains("2008/50");
    assertThat(new ActIdentifier("32015R1998R(01)").number()).isEmpty();
    assertThat(new ActIdentifier("12003T/ACT").number()).isEmpty();
  }

  @Test
  void isCitedByItsNumberAndACorrigendumByTheActsToo() {
    assertThat(new ActIdentifier("32017R0815").cited()).isEqualTo("2017/815");
    assertThat(new ActIdentifier("32019R0103R(01)").cited()).isEqualTo("corrigendum 1 to 2019/103");
    assertThat(new ActIdentifier("12003T/ACT").cited()).isEqualTo("12003T/ACT");
    assertThat(new ActIdentifier("32019R0103R(01)").corrigendum()).isTrue();
    assertThat(new ActIdentifier("32019R0103").corrigendum()).isFalse();
  }

  @Test
  void refusesWhatCantBeOne() {
    assertThat(ActIdentifier.isCelex("")).isFalse();
    assertThat(ActIdentifier.isCelex("32015R1998 x")).isFalse();
    assertThat(ActIdentifier.isCelex("02015R1998-20260322")).isFalse();
  }
}
