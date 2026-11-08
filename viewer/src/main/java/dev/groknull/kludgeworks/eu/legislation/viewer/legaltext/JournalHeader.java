// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The head of the page of the Official Journal where a published act starts, as EUR-Lex prints it
 * in {@code oj-hd-*}. It holds:
 *
 * <ul>
 *   <li>the date, for example "14.11.2015"
 *   <li>the language, for example "EN"
 *   <li>the name of the Journal
 *   <li>the issue and page, for example "L 296/1"
 * </ul>
 */
public record JournalHeader(String date, String language, String journal, String issue) {
  private static final LocalDate NICE = LocalDate.of(2003, 2, 1);
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

  /**
   * Returns the header of {@code text}, from the publication of its source. Returns null in these
   * cases:
   *
   * <ul>
   *   <li>a consolidated version
   *   <li>an act published before February 2003, when the Journal was the Journal of the
   *       Communities
   *   <li>an act whose issue, page or date is not known
   * </ul>
   */
  public static @Nullable JournalHeader of(Element text) {
    var publication = child(child(text, "source"), "publication");
    var lang = text.attribute("xml:lang");
    if (!"original".equals(text.attribute("kind")) || publication == null || lang == null) {
      return null;
    }
    var date = publication.attribute("date");
    var number = publication.attribute("number");
    var page = publication.attribute("page");
    if (date == null || number == null || page == null || LocalDate.parse(date).isBefore(NICE)) {
      return null;
    }
    return new JournalHeader(
        LocalDate.parse(date).format(DATE),
        lang.toUpperCase(Locale.ROOT),
        Language.ofIso(lang).journal(),
        publication.attribute("series") + " " + number + "/" + page);
  }

  private static @Nullable Element child(@Nullable Element element, String name) {
    return element == null
        ? null
        : element.children().stream()
            .filter(child -> child.name().equals(name))
            .findFirst()
            .orElse(null);
  }
}
