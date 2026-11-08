// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.net.URI;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The addresses of documents on EUR-Lex, the official site of EU law. The public methods that give
 * them are on the documents: {@link Eli#eurLex}, {@link ActIdentifier#eurLex}, {@link
 * Version#eurLex} and {@link Act#eurLex}.
 */
final class EurLex {
  private static final String SITE = "https://eur-lex.europa.eu/";

  private EurLex() {}

  /**
   * Returns the address of the original text of the act {@code eli}, in {@code language}. Without a
   * language, EUR-Lex chooses one. For example, {@code
   * https://eur-lex.europa.eu/eli/reg_impl/2015/1998/oj/eng}.
   */
  static URI byEli(Eli eli, @Nullable Language language) {
    return URI.create(
        SITE
            + "eli/"
            + eli.path()
            + "/oj"
            + (language == null ? "" : "/" + language.code().toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns the address of the document {@code celex} in {@code language}. The document can be an
   * act or a consolidated version. For example, {@code
   * https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32015R1998}.
   */
  static URI byCelex(String celex, Language language) {
    return URI.create(
        SITE
            + "legal-content/"
            + language.iso().toUpperCase(Locale.ROOT)
            + "/TXT/?uri=CELEX:"
            + celex);
  }
}
