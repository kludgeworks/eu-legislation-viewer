// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Documents that aren't legal acts are refused, not half-converted (see {@link
 * Manifestations#refused()}).
 */
class RefusedDocumentsTest {

  static final Stylesheet STYLESHEET = new Stylesheet(new Processor(false));

  @ParameterizedTest(name = "{0}")
  @MethodSource("dev.groknull.kludgeworks.formex.converter.Manifestations#refused")
  void isRefusedAsOutOfScope(Manifestation fixture) {
    assertThatExceptionOfType(Stylesheet.Failure.class)
        .isThrownBy(() -> STYLESHEET.transform(fixture.source(), fixture.celex()))
        .satisfies(e -> assertThat(e.code()).isEqualTo(Stylesheet.OUT_OF_SCOPE));
  }
}
