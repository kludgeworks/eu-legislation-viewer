// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The head of the Official Journal's page an act as published starts on, as EUR-Lex prints it
 * (oj-hd-*): its date (14.11.2015), language (EN), the Journal's name and issue/page (L 296/1).
 */
public record JournalHeader(String date, String language, String journal, String issue) {
  private static final LocalDate NICE = LocalDate.of(2003, 2, 1);
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

  /**
   * {@code text}'s header, from its source's publication; none for a consolidated version, an act
   * published before 2003, when the Journal was the Communities', or one whose issue isn't known.
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
