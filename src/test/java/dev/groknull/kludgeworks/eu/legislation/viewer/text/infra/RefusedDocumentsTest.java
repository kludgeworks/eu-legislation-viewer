// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Documents that aren't legal acts are refused, not half-converted (see {@link Fixtures#refused()}). */
class RefusedDocumentsTest {

  static final Normaliser NORMALISER = new Normaliser(new Processor(false));

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.Fixtures#refused")
  void isRefusedAsOutOfScope(Fixture fixture) {
    assertThatExceptionOfType(OutOfScopeException.class)
        .isThrownBy(() -> NORMALISER.normalise(fixture.source(), fixture.celex()));
  }
}
