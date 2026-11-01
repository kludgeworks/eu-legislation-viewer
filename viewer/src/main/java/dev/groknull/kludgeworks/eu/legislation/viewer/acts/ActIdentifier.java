// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act by its CELEX number, as Cellar has it: an act (32015R1998), a corrigendum
 * (32015R1998R(01)), a treaty or a part of one (12003T/ACT), a document of the OJ C series
 * (C2017/413/12, C/2025/03118). The Publications Office assigns them and its forms change, so only
 * what can't be one is refused: an empty value, whitespace, and sector 0, a consolidated version's
 * (02015R1998-20260322), which names a version, not an act. Whether an act has it is Cellar's to
 * say.
 */
@ValueObject
public record ActIdentifier(String celex) {
  private static final Pattern CELEX = Pattern.compile("[^0\\s]\\S*");

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
