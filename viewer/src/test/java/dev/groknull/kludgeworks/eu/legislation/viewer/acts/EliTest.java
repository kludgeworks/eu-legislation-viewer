// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** ELIs as Cellar gives them (counted in Notion: Identifiers and URLs, 2026-10-31). */
class EliTest {

  @Test
  void readsAnActsEli() {
    assertThat(Eli.parse("http://data.europa.eu/eli/reg_impl/2015/1998/oj"))
        .contains(new Eli("reg_impl", 2015, "1998"));
    assertThat(Eli.parse("http://data.europa.eu/eli/reg_impl/2015/1998"))
        .contains(new Eli("reg_impl", 2015, "1998"));
  }

  @Test
  void readsTheOrderOfActsSharingANumber() {
    var eli = Eli.parse("http://data.europa.eu/eli/dec/2007/44(1)/oj").orElseThrow();

    assertThat(eli).isEqualTo(new Eli("dec", 2007, "44(1)"));
    assertThat(eli.path()).isEqualTo("dec/2007/44(1)");
  }

  @Test
  void readsAnActInTheOjCSeries() {
    var eli = Eli.parse("http://data.europa.eu/eli/C/2023/1464/oj").orElseThrow();

    assertThat(eli).isEqualTo(new Eli("C", 2023, "1464"));
    assertThat(eli.path()).isEqualTo("C/2023/1464");
  }

  @Test
  void readsATreatysEli() {
    assertThat(Eli.parse("http://data.europa.eu/eli/treaty/tfeu_2016/oj"))
        .contains(Eli.treaty("tfeu_2016"));
    assertThat(Eli.parse("http://data.europa.eu/eli/treaty/withd_2019(2)/oj").orElseThrow().path())
        .isEqualTo("treaty/withd_2019(2)");
  }

  @Test
  void readsAnEliOfAnyTemplate() {
    assertThat(Eli.parse("http://data.europa.eu/eli/dec/C/2026/400/oj").orElseThrow().path())
        .isEqualTo("dec/C/2026/400");
    assertThat(
            Eli.parse("http://data.europa.eu/eli/reg/1999/1255/corrigendum/1999-10-08/oj")
                .orElseThrow()
                .path())
        .isEqualTo("reg/1999/1255/corrigendum/1999-10-08");
    assertThat(
            Eli.parse("http://data.europa.eu/eli/treaty/tfeu_2008/art_251/oj").orElseThrow().path())
        .isEqualTo("treaty/tfeu_2008/art_251");
  }

  @Test
  void hasNoneForWhatIsntOne() {
    assertThat(Eli.parse("https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj")).isEmpty();
    assertThat(Eli.parse("ELI:L:2023:2616:OJ")).isEmpty();
    assertThat(Eli.parse("http://data.europa.eu/eli/reg_impl/2024//corrigendum/2024-05-15/oj"))
        .isEmpty();
  }

  @Test
  void refusesWhatCantBeAPathOfOne() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Eli(""));
    assertThatIllegalArgumentException().isThrownBy(() -> new Eli("/reg/2015/1998"));
    assertThatIllegalArgumentException().isThrownBy(() -> new Eli("reg//1998"));
    assertThatIllegalArgumentException().isThrownBy(() -> new Eli("reg/2015/1998 x"));
  }
}
