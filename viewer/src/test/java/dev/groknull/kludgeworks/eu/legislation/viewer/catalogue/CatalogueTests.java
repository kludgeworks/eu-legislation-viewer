// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Tests acts through the module's API, kept in Postgres in a container. The module reads an act
 * from Cellar the first time and from the store after that. Cellar is {@link OneActCellar}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, OneActCellar.class})
class CatalogueTests {
  static final ActIdentifier UNKNOWN = new ActIdentifier("32099R9999");

  @Autowired Acts acts;
  @Autowired OneActCellar cellar;

  @Test
  void keepsAnActReadFromCellarWhole() {
    var read = acts.find(OneActCellar.CELEX);
    var kept = acts.find(OneActCellar.CELEX);

    assertThat(read).contains(OneActCellar.ACT);
    assertThat(kept).contains(OneActCellar.ACT);
    assertThat(cellar.asked).containsOnlyOnce(OneActCellar.CELEX.celex());
  }

  @Test
  void findsAKeptActByItsEli() {
    acts.find(OneActCellar.CELEX);

    assertThat(acts.find(OneActCellar.ELI)).contains(OneActCellar.ACT);
    assertThat(cellar.asked).doesNotContain("ELI " + OneActCellar.ELI);
  }

  @Test
  void keepsNothingCellarDoesntHave() {
    assertThat(acts.find(UNKNOWN)).isEmpty();
    assertThat(acts.find(UNKNOWN)).isEmpty();
    assertThat(cellar.asked).containsSubsequence(UNKNOWN.celex(), UNKNOWN.celex());
  }
}
