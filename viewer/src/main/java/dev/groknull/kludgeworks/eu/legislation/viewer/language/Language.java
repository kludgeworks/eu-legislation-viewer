// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import java.util.Arrays;
import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One of the 24 official languages of the EU, by the code of the EU's language authority table
 * (Cellar's: ENG) and by ISO 639-1 (the legal text's xml:lang: en).
 */
@ValueObject
public enum Language {
  BUL("bg"),
  CES("cs"),
  DAN("da"),
  DEU("de"),
  ELL("el"),
  ENG("en"),
  EST("et"),
  FIN("fi"),
  FRA("fr"),
  GLE("ga"),
  HRV("hr"),
  HUN("hu"),
  ITA("it"),
  LAV("lv"),
  LIT("lt"),
  MLT("mt"),
  NLD("nl"),
  POL("pl"),
  POR("pt"),
  RON("ro"),
  SLK("sk"),
  SLV("sl"),
  SPA("es"),
  SWE("sv");

  private final String iso;

  Language(String iso) {
    this.iso = iso;
  }

  /** The EU authority table's code: ENG, as Cellar names the language. */
  public String code() {
    return name();
  }

  /** ISO 639-1: en, as the legal text's xml:lang, HTTP's and the API's language tags have it. */
  public String iso() {
    return iso;
  }

  /**
   * The language with this code of the EU authority table (ENG; eng, as an ELI has it), in either
   * case.
   */
  public static Language ofCode(String code) {
    return Arrays.stream(values())
        .filter(language -> language.name().equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not an official EU language: " + code));
  }

  /** The language with this ISO 639-1 code (en), in either case. */
  public static Language ofIso(String iso) {
    return Arrays.stream(values())
        .filter(language -> language.iso.equals(iso.toLowerCase(Locale.ROOT)))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not an official EU language: " + iso));
  }
}
