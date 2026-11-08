// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/**
 * Helpers that all pages use to read a request or to answer it in the same way:
 *
 * <ul>
 *   <li>the language of the request
 *   <li>today
 *   <li>not found
 *   <li>the media type of a page
 * </ul>
 */
public final class Requests {
  /**
   * The time zone of the EU day: Brussels. The EU adopts its acts there, and the OJ dates them
   * there.
   */
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  /**
   * The media type of a page, with its charset. A controller writes a page that it renders to a
   * String in UTF-8. Without the charset in the type, a client that does not read the meta charset
   * of the page reads it as Latin-1. An HTTP client is an example.
   */
  public static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

  private Requests() {}

  /**
   * Returns the language for its code in the path, for example {@code eng}. Throws not found for a
   * language that the EU does not have.
   */
  public static Language language(String code) {
    try {
      return Language.ofCode(code);
    } catch (IllegalArgumentException e) {
      throw notFound();
    }
  }

  /** Returns today, in Brussels. */
  public static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), BRUSSELS);
  }

  /** Returns the exception that answers not found, 404. */
  public static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
