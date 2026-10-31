// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's European Legislation Identifier, by its path below http://data.europa.eu/eli/. An act's
 * is its type, year and number: reg_impl/2015/1998. The type is the ELI's (reg_impl), finer than
 * the CELEX's (R); for an act published in the OJ C series since the act-by-act Official Journal (1
 * October 2023), the series, {@code C}: C/2023/1464 is Commission Decision 32023D01464. Where
 * several acts of a year share a number, it carries their order: dec/2007/44(1). A treaty's is its
 * code and the year of its edition, treaty/tfeu_2016, with the order where editions of a year share
 * one: treaty/withd_2019(1). A treaty's articles have ELIs of their own (treaty/tfeu_2016/art_251),
 * not of this shape.
 */
@ValueObject
public record Eli(String path) {
  private static final String ACT = "(?:[a-z][a-z_]*|C)/[0-9]{4}/[0-9]+(?:\\([0-9]+\\))?";
  private static final String TREATY = "treaty/[a-z]+_[0-9]{4}(?:\\([0-9]+\\))?";
  private static final Pattern PATH = Pattern.compile(ACT + "|" + TREATY);
  private static final Pattern URI =
      Pattern.compile("http://data\\.europa\\.eu/eli/(" + ACT + "|" + TREATY + ")(?:/oj)?");

  public Eli {
    if (!PATH.matcher(path).matches()) {
      throw new IllegalArgumentException("Not an ELI: " + path);
    }
  }

  /** An act's: reg_impl, 2015, 1998. */
  public Eli(String type, int year, String number) {
    this(type + "/" + year + "/" + number);
  }

  /** A treaty's, by its code and edition: tfeu_2016. */
  public static Eli treaty(String edition) {
    return new Eli("treaty/" + edition);
  }

  /**
   * The ELI of the original text or of the act, http://data.europa.eu/eli/reg_impl/2015/1998[/oj]
   * or http://data.europa.eu/eli/treaty/tfeu_2016[/oj]; none for an ELI of another shape (a
   * treaty's article, a corrigendum).
   */
  public static Optional<Eli> parse(String uri) {
    var parts = URI.matcher(uri);
    return parts.matches() ? Optional.of(new Eli(parts.group(1))) : Optional.empty();
  }

  @Override
  public String toString() {
    return path;
  }
}
