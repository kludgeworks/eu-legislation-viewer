// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the pages read from a request, or answer, the same way: its language, today, not found, a
 * page's type.
 */
final class Requests {
  /** Where the day is the EU's: Brussels, where its acts are adopted and the OJ dates them. */
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  /**
   * A page's type, its charset said: a page a controller renders to a String is written in UTF-8,
   * but without it said, a client without the page's meta charset (an HTTP client) reads Latin-1.
   */
  static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

  private Requests() {}

  /** The language by its code in the path (eng); not found for one the EU doesn't have. */
  static Language language(String code) {
    try {
      return Language.ofCode(code);
    } catch (IllegalArgumentException e) {
      throw notFound();
    }
  }

  /** Today, in Brussels. */
  static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), BRUSSELS);
  }

  static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
