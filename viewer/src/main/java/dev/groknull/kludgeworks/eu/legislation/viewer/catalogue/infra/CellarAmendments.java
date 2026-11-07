// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment.Role;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An act's amendments from the answer to amendments.rq: each amended location with its role
 * (fd_375), its days and the languages it is limited to. A role this doesn't know is kept as OTHER,
 * with a warning; a placeholder for a day is none.
 */
final class CellarAmendments {
  private static final Logger LOG = LoggerFactory.getLogger(CellarAmendments.class);

  /** An authority code as Cellar writes it in an annotation: {R|http://…/fd_375/R}. */
  private static final Pattern CODE = Pattern.compile("\\{([^|}]*)\\|[^}]*}");

  private CellarAmendments() {}

  /** The amendments in the answer, in its order; those by a subject that isn't an act left out. */
  static List<Amendment> of(ActIdentifier base, SparqlResult answer) {
    var amendments = new ArrayList<Amendment>();
    var unknown = new TreeSet<String>();
    for (var row : answer.rows()) {
      var subject = row.get("subject");
      if (!ActIdentifier.isCelex(subject)) {
        continue;
      }
      var code = row.find("role").map(CellarAmendments::code).orElse("");
      var role = Role.of(code);
      if (role.isEmpty()) {
        unknown.add(code);
      }
      amendments.add(
          new Amendment(
              new ActIdentifier(subject),
              Location.parse(row.get("location")),
              role.orElse(Role.OTHER),
              row.find("from").map(CellarDates::day).orElse(null),
              row.find("until").map(CellarDates::day).orElse(null),
              languages(row.find("languages").orElse(""))));
    }
    if (!unknown.isEmpty()) {
      LOG.warn("{}: amendments of a role this doesn't know, kept as OTHER: {}", base, unknown);
    }
    return amendments;
  }

  /**
   * The languages of Cellar's list of them: SV, PL (ISO 639-1); none for none, and those it doesn't
   * know left out.
   */
  private static Set<Language> languages(String list) {
    var languages = EnumSet.noneOf(Language.class);
    for (var iso : list.toLowerCase(Locale.ROOT).split("[\\s,;]+")) {
      Arrays.stream(Language.values())
          .filter(language -> language.iso().equals(iso))
          .forEach(languages::add);
    }
    return languages;
  }

  /** The code of an authority value ({R|…} is R), or the value if it isn't one. */
  private static String code(String value) {
    var matcher = CODE.matcher(value);
    return matcher.find() ? matcher.group(1) : value;
  }
}
