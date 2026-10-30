// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act by its CELEX number: sector, year, type and number (32015R1998), with a suffix for a
 * corrigendum (32015R1998R(01)) or one of several documents under one number (32012A1221(01)), or a
 * treaty or a part of one (11985I, 12003T/ACT). The schema's Celex type, without what names a
 * version and not an act: sector 0 and the date of a consolidated version (02015R1998-20260322).
 */
@ValueObject
public record ActIdentifier(String celex) {
  private static final Pattern CELEX =
      Pattern.compile("[1-9A-Z][0-9]{4}[A-Z]{1,2}[0-9A-Z()]*(/[0-9A-Z()]+)*");

  public ActIdentifier {
    if (!isCelex(celex)) {
      throw new IllegalArgumentException("Not an act's CELEX number: " + celex);
    }
  }

  /** Whether {@code value} is an act's CELEX number. */
  public static boolean isCelex(String value) {
    return CELEX.matcher(value).matches();
  }

  @Override
  public String toString() {
    return celex;
  }
}
