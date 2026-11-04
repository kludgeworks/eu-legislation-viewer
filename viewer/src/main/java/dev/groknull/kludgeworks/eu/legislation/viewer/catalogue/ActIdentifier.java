// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
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

  /** A numbered act of secondary law (sector 3): its year, its type, its number (32015R1998). */
  private static final Pattern NUMBERED = Pattern.compile("3(\\d{4})([A-Z])0*(\\d+)");

  /** The year acts were first numbered year first (Regulation (EU) 2015/2240 on). */
  private static final int YEAR_FIRST = 2015;

  public ActIdentifier {
    if (!isCelex(celex)) {
      throw new IllegalArgumentException("Not an act's CELEX number: " + celex);
    }
  }

  /**
   * The act's number as the Official Journal cites it: 2015/1998, 2019/103; a regulation before
   * 2015 number first, 185/2010 (Regulation (EU) No 185/2010). None for any other CELEX: a treaty,
   * a corrigendum, a document of the C series.
   */
  public Optional<String> number() {
    var parts = NUMBERED.matcher(celex);
    if (!parts.matches()) {
      return Optional.empty();
    }
    var year = parts.group(1);
    var number = parts.group(3);
    return Optional.of(
        parts.group(2).equals("R") && Integer.parseInt(year) < YEAR_FIRST
            ? number + "/" + year
            : year + "/" + number);
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
