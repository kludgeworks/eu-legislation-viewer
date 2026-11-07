// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.language;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One of the 24 official languages of the EU. It has the code of the EU's language authority table,
 * which Cellar uses, for example ENG. It also has its ISO 639-1 code, which the legal text uses in
 * {@code xml:lang}, for example en.
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
   * The EU's protocol order. It sorts the languages by their names in themselves, as section 7.2.1
   * of the Interinstitutional Style Guide says. EUR-Lex lists the languages in this order.
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

  /**
   * Returns the 24 languages in the EU's protocol order. For example, български, español, čeština …
   * suomi, svenska.
   */
  public static List<Language> inProtocolOrder() {
    return PROTOCOL;
  }

  /**
   * Returns its name in English, for example Irish. The reason why a version lacks the language
   * uses this name.
   */
  public String english() {
    return english;
  }

  /** Returns the code of the EU authority table, for example ENG. Cellar names the language so. */
  public String code() {
    return name();
  }

  /**
   * Returns the ISO 639-1 code, for example en. The {@code xml:lang} of the legal text and the
   * language tags of HTTP and of the API use this code.
   */
  public String iso() {
    return iso;
  }

  /**
   * Returns its name in itself, for example English, Deutsch or français. EUR-Lex lists the
   * languages by this name.
   */
  public String endonym() {
    return endonym;
  }

  /**
   * Returns the name of the Official Journal in this language, as its header prints it. Since the
   * Treaty of Nice, on 1 February 2003, the name is "Official Journal of the European Union".
   */
  public String journal() {
    return journal;
  }

  /**
   * Returns the language with this code of the EU authority table, in upper or lower case. For
   * example, ENG, or eng as an ELI has it.
   */
  public static Language ofCode(String code) {
    return Arrays.stream(values())
        .filter(language -> language.name().equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not an official EU language: " + code));
  }

  /** Returns the language with this ISO 639-1 code, for example en, in upper or lower case. */
  public static Language ofIso(String iso) {
    return Arrays.stream(values())
        .filter(language -> language.iso.equals(iso.toLowerCase(Locale.ROOT)))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not an official EU language: " + iso));
  }
}
