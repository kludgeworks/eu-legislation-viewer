// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** What the act's pages read from a request the same way: its language, today, not found. */
final class Requests {
  /** Where the day is the EU's: Brussels, where its acts are adopted and the OJ dates them. */
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

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
