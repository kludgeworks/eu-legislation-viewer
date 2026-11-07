// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Tests how the pages write dates, acts and provisions, with {@link Wording}. */
class WordingTest {

  @Test
  void writesADateAsTheDesignDoes() {
    assertThat(Wording.date(LocalDate.of(2017, 6, 1))).isEqualTo("1 June 2017");
  }

  @Test
  void namesAnActByItsNumberAndACorrigendumByTheActsToo() {
    assertThat(Wording.act(new ActIdentifier("32017R0815"))).isEqualTo("2017/815");
    assertThat(Wording.act(new ActIdentifier("32019R0103R(01)")))
        .isEqualTo("corrigendum 1 to 2019/103");
    assertThat(Wording.act(new ActIdentifier("12003T/ACT"))).isEqualTo("12003T/ACT");
  }

  @Test
  void namesAProvisionAsTheActDoes() {
    assertThat(Wording.provision(new Provision(Provision.Type.ARTICLE, "2")))
        .isEqualTo("Article 2");
    assertThat(Wording.provision(new Provision(Provision.Type.ANNEX, null))).isEqualTo("Annex");
  }
}
