// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's European Legislation Identifier, by its path below http://data.europa.eu/eli/, without
 * the /oj of its original text: reg_impl/2015/1998, C/2023/1464 (the act-by-act OJ C series),
 * dec/2007/44(1) (acts of a year sharing a number), treaty/tfeu_2016 (a treaty's edition). ELI
 * leaves its templates to each publisher and the Publications Office's are many, so only what can't
 * be one is refused: an empty path or segment, whitespace. Whether an act has it is Cellar's to
 * say.
 */
@ValueObject
public record Eli(String path) {
  private static final String BASE = "http://data.europa.eu/eli/";

  public Eli {
    if (path.isEmpty()
        || path.startsWith("/")
        || path.endsWith("/")
        || path.contains("//")
        || path.chars().anyMatch(Character::isWhitespace)) {
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
   * The ELI {@code uri} is, http://data.europa.eu/eli/reg_impl/2015/1998[/oj]; none for a URI not
   * below http://data.europa.eu/eli/.
   */
  public static Optional<Eli> parse(String uri) {
    if (!uri.startsWith(BASE)) {
      return Optional.empty();
    }
    var path = uri.substring(BASE.length());
    path = path.endsWith("/oj") ? path.substring(0, path.length() - "/oj".length()) : path;
    try {
      return Optional.of(new Eli(path));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return path;
  }
}
