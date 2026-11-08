// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import org.junit.jupiter.api.Test;

/** Tests how a provision of an act is cited, with {@link Provision#cited}. */
class ActDateTest {

  @Test
  void citesAProvisionAsTheActDoes() {
    assertThat(new Provision(Provision.Type.ARTICLE, "2").cited()).isEqualTo("Article 2");
    assertThat(new Provision(Provision.Type.ANNEX, null).cited()).isEqualTo("Annex");
  }
}
