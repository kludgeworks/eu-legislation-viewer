// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication.Journal;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads how the amending acts of an act were published, from the answer to {@code publications.rq}.
 * For each act it reads the ELI, the title in each official language, and the place in the Official
 * Journal. The place is the number in the act-by-act Journal if the act has one. If not, it is the
 * issue that contains the act.
 */
final class CellarPublications {
  private CellarPublications() {}

  /** Returns the publication of each act in the answer, by act. It leaves out consolidations. */
  static Map<ActIdentifier, Publication> byAct(SparqlResult answer) {
    var elis = new LinkedHashMap<ActIdentifier, @Nullable Eli>();
    var titles = new LinkedHashMap<ActIdentifier, Map<Language, String>>();
    var journals = new LinkedHashMap<ActIdentifier, Journal>();
    for (var row : answer.rows()) {
      var subject = row.get("subject");
      // The CELEX number of a consolidation starts with 0, for example 02015R1998-20260322.
      if (!ActIdentifier.isCelex(subject) || subject.startsWith("0")) {
        continue;
      }
      var act = new ActIdentifier(subject);
      elis.putIfAbsent(act, row.find("eli").flatMap(Eli::parse).orElse(null));
      var forAct = titles.computeIfAbsent(act, each -> new EnumMap<>(Language.class));
      row.find("code")
          .flatMap(CellarPublications::language)
          .ifPresent(language -> row.find("title").ifPresent(t -> forAct.putIfAbsent(language, t)));
      actByAct(row).or(() -> inIssue(row)).ifPresent(journal -> journals.putIfAbsent(act, journal));
    }
    var publications = new LinkedHashMap<ActIdentifier, Publication>();
    elis.forEach(
        (act, eli) ->
            publications.put(
                act, new Publication(eli, titles.getOrDefault(act, Map.of()), journals.get(act))));
    return publications;
  }

  /**
   * Returns the place of the act in the act-by-act Journal. For example, OJ L, 2026/449, 2.3.2026.
   * It returns empty if the row has no series, number or day for it.
   */
  private static Optional<Journal> actByAct(SparqlResult.Row row) {
    var collection = row.find("collection");
    var number = row.find("number");
    var published = row.find("published");
    if (collection.isEmpty() || number.isEmpty() || published.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Journal(series(collection.get()), number.get(), null, day(published.get())));
  }

  /**
   * Returns the place of the act in an issue of the Journal. For example, OJ L 21, 24.1.2019. It
   * returns empty if the row has no series, issue or day for it.
   */
  private static Optional<Journal> inIssue(SparqlResult.Row row) {
    var collection = row.find("issueCollection");
    var issue = row.find("issue");
    var published = row.find("issuePublished");
    if (collection.isEmpty() || issue.isEmpty() || published.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Journal(
            series(collection.get()), null, Integer.valueOf(issue.get()), day(published.get())));
  }

  /**
   * Returns the series from the URI of a document collection. For example, {@code L} for {@code
   * .../OJ-L}.
   */
  private static String series(String collection) {
    return collection.substring(collection.lastIndexOf('/') + 1).replaceFirst("^OJ-", "");
  }

  /** Returns the day of an {@code xsd:date} or {@code xsd:dateTime}. */
  private static LocalDate day(String published) {
    return LocalDate.parse(published.substring(0, 10));
  }

  private static Optional<Language> language(String code) {
    for (var language : Language.values()) {
      if (language.code().equals(code)) {
        return Optional.of(language);
      }
    }
    return Optional.empty();
  }
}
