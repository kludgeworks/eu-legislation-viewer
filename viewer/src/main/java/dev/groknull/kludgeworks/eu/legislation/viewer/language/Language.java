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
  BUL("bg", "български", "Официален вестник на Европейския съюз"),
  CES("cs", "čeština", "Úřední věstník Evropské unie"),
  DAN("da", "dansk", "Den Europæiske Unions Tidende"),
  DEU("de", "Deutsch", "Amtsblatt der Europäischen Union"),
  ELL("el", "ελληνικά", "Επίσημη Εφημερίδα της Ευρωπαϊκής Ένωσης"),
  ENG("en", "English", "Official Journal of the European Union"),
  EST("et", "eesti keel", "Euroopa Liidu Teataja"),
  FIN("fi", "suomi", "Euroopan unionin virallinen lehti"),
  FRA("fr", "français", "Journal officiel de l'Union européenne"),
  GLE("ga", "Gaeilge", "Iris Oifigiúil an Aontais Eorpaigh"),
  HRV("hr", "hrvatski", "Službeni list Europske unije"),
  HUN("hu", "magyar", "Az Európai Unió Hivatalos Lapja"),
  ITA("it", "italiano", "Gazzetta ufficiale dell'Unione europea"),
  LAV("lv", "latviešu valoda", "Eiropas Savienības Oficiālais Vēstnesis"),
  LIT("lt", "lietuvių kalba", "Europos Sąjungos oficialusis leidinys"),
  MLT("mt", "Malti", "Il-Ġurnal Uffiċjali tal-Unjoni Ewropea"),
  NLD("nl", "Nederlands", "Publicatieblad van de Europese Unie"),
  POL("pl", "polski", "Dziennik Urzędowy Unii Europejskiej"),
  POR("pt", "português", "Jornal Oficial da União Europeia"),
  RON("ro", "română", "Jurnalul Oficial al Uniunii Europene"),
  SLK("sk", "slovenčina", "Úradný vestník Európskej únie"),
  SLV("sl", "slovenščina", "Uradni list Evropske unije"),
  SPA("es", "español", "Diario Oficial de la Unión Europea"),
  SWE("sv", "svenska", "Europeiska unionens officiella tidning");

  private final String iso;
  private final String endonym;
  private final String journal;

  Language(String iso, String endonym, String journal) {
    this.iso = iso;
    this.endonym = endonym;
    this.journal = journal;
  }

  /** The EU authority table's code: ENG, as Cellar names the language. */
  public String code() {
    return name();
  }

  /** ISO 639-1: en, as the legal text's xml:lang, HTTP's and the API's language tags have it. */
  public String iso() {
    return iso;
  }

  /** Its name in itself, as EUR-Lex lists the languages: English, Deutsch, français. */
  public String endonym() {
    return endonym;
  }

  /**
   * The Official Journal's name in this language, as its header prints it since the Treaty of Nice
   * (1 February 2003): of the European Union.
   */
  public String journal() {
    return journal;
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
