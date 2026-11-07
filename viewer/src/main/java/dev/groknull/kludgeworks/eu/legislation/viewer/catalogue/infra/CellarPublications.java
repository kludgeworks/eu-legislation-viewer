// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication.Journal;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * How the acts an act's versions integrate were published, from the answer to publications.rq: each
 * act's ELI, its title in each official language, and its place in the Official Journal, by its
 * number in the act-by-act Journal where it has one, else by the issue it is in.
 */
final class CellarPublications {
  private CellarPublications() {}

  /** Each act's publication in the answer, by the act; consolidations left out. */
  static Map<ActIdentifier, Publication> byAct(SparqlResult answer) {
    var elis = new LinkedHashMap<ActIdentifier, @Nullable Eli>();
    var titles = new LinkedHashMap<ActIdentifier, Map<Language, String>>();
    var journals = new LinkedHashMap<ActIdentifier, Journal>();
    for (var row : answer.rows()) {
      var subject = row.get("subject");
      // a consolidation's CELEX starts with 0 (02015R1998-20260322)
      if (!ActIdentifier.isCelex(subject) || subject.startsWith("0")) {
        continue;
      }
      var act = new ActIdentifier(subject);
      elis.putIfAbsent(act, row.find("eli").flatMap(Eli::parse).orElse(null));
      var forAct = titles.computeIfAbsent(act, each -> new EnumMap<>(Language.class));
      row.find("code")
          .flatMap(CellarPublications::language)
          .ifPresent(language -> row.find("title").ifPresent(t -> forAct.putIfAbsent(language, t)));
      journal(row.find("collection"), row.find("number"), null, row.find("published"))
          .or(
              () ->
                  journal(
                      row.find("issueCollection"),
                      Optional.empty(),
                      row.find("issue").map(Integer::valueOf).orElse(null),
                      row.find("issuePublished")))
          .ifPresent(journal -> journals.putIfAbsent(act, journal));
    }
    var publications = new LinkedHashMap<ActIdentifier, Publication>();
    elis.forEach(
        (act, eli) ->
            publications.put(
                act, new Publication(eli, titles.getOrDefault(act, Map.of()), journals.get(act))));
    return publications;
  }

  /** The place in the Journal; none without its series or day. */
  private static Optional<Journal> journal(
      Optional<String> collection,
      Optional<String> number,
      @Nullable Integer issue,
      Optional<String> published) {
    if (collection.isEmpty() || published.isEmpty() || (number.isEmpty() && issue == null)) {
      return Optional.empty();
    }
    // the document collection OJ-L is the L series
    var uri = collection.get();
    var series = uri.substring(uri.lastIndexOf('/') + 1).replaceFirst("^OJ-", "");
    return Optional.of(
        new Journal(
            series, number.orElse(null), issue, LocalDate.parse(published.get().substring(0, 10))));
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
