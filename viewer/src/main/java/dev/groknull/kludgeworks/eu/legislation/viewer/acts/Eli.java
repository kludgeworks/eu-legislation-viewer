// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's European Legislation Identifier, as its type, year and number:
 * http://data.europa.eu/eli/reg_impl/2015/1998 is {@code reg_impl}, 2015, {@code 1998}. The type is
 * the ELI's (reg_impl), finer than the CELEX's (R).
 */
@ValueObject
public record Eli(String type, int year, String number) {
  private static final Pattern TYPE = Pattern.compile("[a-z][a-z_]*");
  private static final Pattern NUMBER = Pattern.compile("[0-9]+");
  private static final Pattern URI =
      Pattern.compile("http://data\\.europa\\.eu/eli/([a-z][a-z_]*)/([0-9]{4})/([0-9]+)(/oj)?");

  public Eli {
    if (!TYPE.matcher(type).matches()
        || year < 1000
        || year > 9999
        || !NUMBER.matcher(number).matches()) {
      throw new IllegalArgumentException("Not an ELI: " + type + "/" + year + "/" + number);
    }
  }

  /**
   * The ELI of the original text or of the act, http://data.europa.eu/eli/reg_impl/2015/1998[/oj].
   */
  public static Eli of(String uri) {
    var parts = URI.matcher(uri);
    if (!parts.matches()) {
      throw new IllegalArgumentException("Not an act's ELI: " + uri);
    }
    return new Eli(parts.group(1), Integer.parseInt(parts.group(2)), parts.group(3));
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
