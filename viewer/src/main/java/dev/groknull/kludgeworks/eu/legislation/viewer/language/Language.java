// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One of the 24 official languages of the EU, by the code of the EU's language authority table
 * (Cellar's: ENG) and by ISO 639-1 (the legal text's xml:lang: en).
 */
@ValueObject
public enum Language {
  BUL("bg", "Bulgarian", "български", "Официален вестник на Европейския съюз"),
  CES("cs", "Czech", "čeština", "Úřední věstník Evropské unie"),
  DAN("da", "Danish", "dansk", "Den Europæiske Unions Tidende"),
  DEU("de", "German", "Deutsch", "Amtsblatt der Europäischen Union"),
  ELL("el", "Greek", "ελληνικά", "Επίσημη Εφημερίδα της Ευρωπαϊκής Ένωσης"),
  ENG("en", "English", "English", "Official Journal of the European Union"),
  EST("et", "Estonian", "eesti keel", "Euroopa Liidu Teataja"),
  FIN("fi", "Finnish", "suomi", "Euroopan unionin virallinen lehti"),
  FRA("fr", "French", "français", "Journal officiel de l'Union européenne"),
  GLE("ga", "Irish", "Gaeilge", "Iris Oifigiúil an Aontais Eorpaigh"),
  HRV("hr", "Croatian", "hrvatski", "Službeni list Europske unije"),
  HUN("hu", "Hungarian", "magyar", "Az Európai Unió Hivatalos Lapja"),
  ITA("it", "Italian", "italiano", "Gazzetta ufficiale dell'Unione europea"),
  LAV("lv", "Latvian", "latviešu valoda", "Eiropas Savienības Oficiālais Vēstnesis"),
  LIT("lt", "Lithuanian", "lietuvių kalba", "Europos Sąjungos oficialusis leidinys"),
  MLT("mt", "Maltese", "Malti", "Il-Ġurnal Uffiċjali tal-Unjoni Ewropea"),
  NLD("nl", "Dutch", "Nederlands", "Publicatieblad van de Europese Unie"),
  POL("pl", "Polish", "polski", "Dziennik Urzędowy Unii Europejskiej"),
  POR("pt", "Portuguese", "português", "Jornal Oficial da União Europeia"),
  RON("ro", "Romanian", "română", "Jurnalul Oficial al Uniunii Europene"),
  SLK("sk", "Slovak", "slovenčina", "Úradný vestník Európskej únie"),
  SLV("sl", "Slovenian", "slovenščina", "Uradni list Evropske unije"),
  SPA("es", "Spanish", "español", "Diario Oficial de la Unión Europea"),
  SWE("sv", "Swedish", "svenska", "Europeiska unionens officiella tidning");

  /**
   * The EU's protocol order: by each language's name in itself (Interinstitutional Style Guide,
   * 7.2.1), as EUR-Lex lists them.
   */
  private static final List<Language> PROTOCOL =
      List.of(
          BUL, SPA, CES, DAN, DEU, EST, ELL, ENG, FRA, GLE, HRV, ITA, LAV, LIT, HUN, MLT, NLD, POL,
          POR, RON, SLK, SLV, FIN, SWE);

  private final String iso;
  private final String english;
  private final String endonym;
  private final String journal;

  Language(String iso, String english, String endonym, String journal) {
    this.iso = iso;
    this.english = english;
    this.endonym = endonym;
    this.journal = journal;
  }

  /** The 24, in the EU's protocol order: български, español, čeština … suomi, svenska. */
  public static List<Language> inProtocolOrder() {
    return PROTOCOL;
  }

  /** Its name in English: Irish, as a reason a version lacks it says it. */
  public String english() {
    return english;
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
