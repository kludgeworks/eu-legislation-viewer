// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Documents that aren't legal acts are refused, not half-converted (see {@link
 * Manifestations#refused()}).
 */
class RefusedDocumentsTest {

  static final FormexConverter CONVERTER = new FormexConverter(new Processor(false));

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.groknull.kludgeworks.formex.converter.Manifestations#refused")
  void isRefusedAsOutOfScope(Manifestation fixture) {
    assertThatExceptionOfType(OutOfScopeException.class)
        .isThrownBy(() -> CONVERTER.convert(fixture.source(), fixture.celex()));
  }
}
