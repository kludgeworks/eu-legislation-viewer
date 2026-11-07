// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act by its CELEX number, as Cellar has it. The number can identify, for example:
 *
 * <ul>
 *   <li>an act, {@code 32015R1998}
 *   <li>a corrigendum, {@code 32015R1998R(01)}
 *   <li>a treaty or a part of one, {@code 12003T/ACT}
 *   <li>a document of the OJ C series, {@code C2017/413/12} or {@code C/2025/03118}.
 * </ul>
 *
 * <p>The Publications Office assigns the numbers, and their forms change. Thus only a value that
 * cannot be a CELEX number of an act is refused:
 *
 * <ul>
 *   <li>an empty value
 *   <li>a value with whitespace
 *   <li>a value in sector 0. This is the number of a consolidated version, for example {@code
 *       02015R1998-20260322}. It identifies a version, not an act.
 * </ul>
 *
 * <p>Cellar says whether an act has the number.
 */
@ValueObject
public record ActIdentifier(String celex) {
  private static final Pattern CELEX = Pattern.compile("[^0\\s]\\S*");

  /**
   * Matches a numbered act of secondary law, in sector 3. It captures the year, the type and the
   * number of the act. For example, {@code 32015R1998}.
   */
  private static final Pattern NUMBERED = Pattern.compile("3(\\d{4})([A-Z])0*(\\d+)");

  /**
   * The year from which acts are numbered year first. The numbering starts with Regulation (EU)
   * 2015/2240.
   */
  private static final int YEAR_FIRST = 2015;

  public ActIdentifier {
    if (!isCelex(celex)) {
      throw new IllegalArgumentException("Not an act's CELEX number: " + celex);
    }
  }

  /**
   * Returns the act's number as the Official Journal cites it, for example 2015/1998 or 2019/103. A
   * regulation before 2015 has its number first. For example, 185/2010 for Regulation (EU) No
   * 185/2010. Returns empty for any other CELEX, such as a treaty, a corrigendum or a document of
   * the C series.
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
