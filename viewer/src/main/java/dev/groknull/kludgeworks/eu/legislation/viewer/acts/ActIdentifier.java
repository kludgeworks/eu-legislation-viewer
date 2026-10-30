// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act by its CELEX number: sector, year, type and number (32015R1998), and for a corrigendum its
 * suffix (32015R1998R(01)). A consolidated version's CELEX (02015R1998-20260322) names a version of
 * an act, not an act.
 */
@ValueObject
public record ActIdentifier(String celex) {
  private static final Pattern CELEX =
      Pattern.compile("[1-9CE][0-9]{4}[A-Z]{1,2}[0-9]{4}(R?\\([0-9]{2}\\))?");

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
