// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's European Legislation Identifier, as its type, year and number:
 * http://data.europa.eu/eli/reg_impl/2015/1998 is {@code reg_impl}, 2015, {@code 1998}. The type is
 * the ELI's (reg_impl), finer than the CELEX's (R); for an act published in the OJ C series since
 * the act-by-act Official Journal (1 October 2023), the series, {@code C}: C/2023/1464 is
 * Commission Decision 32023D01464. Where several acts of a year share a number, it carries their
 * order: dec/2007/44(1). Treaties' ELIs, by article, are of another shape.
 */
@ValueObject
public record Eli(String type, int year, String number) {
  private static final String TYPES = "[a-z][a-z_]*|C";
  private static final String NUMBERS = "[0-9]+(?:\\([0-9]+\\))?";
  private static final Pattern TYPE = Pattern.compile(TYPES);
  private static final Pattern NUMBER = Pattern.compile(NUMBERS);
  private static final Pattern URI =
      Pattern.compile(
          "http://data\\.europa\\.eu/eli/(" + TYPES + ")/([0-9]{4})/(" + NUMBERS + ")(?:/oj)?");

  public Eli {
    if (!TYPE.matcher(type).matches()
        || year < 1000
        || year > 9999
        || !NUMBER.matcher(number).matches()) {
      throw new IllegalArgumentException("Not an ELI: " + type + "/" + year + "/" + number);
    }
  }

  /**
   * The ELI of the original text or of the act, http://data.europa.eu/eli/reg_impl/2015/1998[/oj];
   * none for an ELI of another shape (a treaty's article, a corrigendum).
   */
  public static Optional<Eli> parse(String uri) {
    var parts = URI.matcher(uri);
    if (!parts.matches()) {
      return Optional.empty();
    }
    return Optional.of(new Eli(parts.group(1), Integer.parseInt(parts.group(2)), parts.group(3)));
  }

  /** reg_impl/2015/1998: the ELI's path below http://data.europa.eu/eli/. */
  public String path() {
    return type + "/" + year + "/" + number;
  }

  @Override
  public String toString() {
    return path();
  }
}
