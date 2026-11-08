// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * An act's European Legislation Identifier, by its path below {@code http://data.europa.eu/eli/}.
 * The path does not include the {@code /oj} of the original text. For example:
 *
 * <ul>
 *   <li>{@code reg_impl/2015/1998}
 *   <li>{@code C/2023/1464}, in the act-by-act OJ C series
 *   <li>{@code dec/2007/44(1)}, for acts of a year that share a number
 *   <li>{@code treaty/tfeu_2016}, for an edition of a treaty.
 * </ul>
 *
 * <p>ELI leaves its templates to each publisher, and the Publications Office has many. Thus only a
 * value that cannot be an ELI is refused: an empty path, an empty segment, or whitespace. Cellar
 * says whether an act has the ELI.
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

  /** Makes the ELI of an act, for example from reg_impl, 2015 and 1998. */
  public Eli(String type, int year, String number) {
    this(type + "/" + year + "/" + number);
  }

  /** Returns the ELI of a treaty, by its code and edition, for example {@code tfeu_2016}. */
  public static Eli treaty(String edition) {
    return new Eli("treaty/" + edition);
  }

  /**
   * Returns the ELI that {@code uri} is. For example, {@code
   * http://data.europa.eu/eli/reg_impl/2015/1998}, with or without {@code /oj}. Returns empty for a
   * URI that is not below {@code http://data.europa.eu/eli/}, or that is not a valid ELI.
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

  /**
   * Returns the address of the act's original text on EUR-Lex. EUR-Lex chooses the language. For
   * example, {@code https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj}.
   */
  public URI eurLex() {
    return EurLex.byEli(this, null);
  }

  /**
   * Returns the address of the act's original text on EUR-Lex, in {@code language}. For example,
   * {@code https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj/eng}.
   */
  public URI eurLex(Language language) {
    return EurLex.byEli(this, language);
  }

  @Override
  public String toString() {
    return path;
  }
}
