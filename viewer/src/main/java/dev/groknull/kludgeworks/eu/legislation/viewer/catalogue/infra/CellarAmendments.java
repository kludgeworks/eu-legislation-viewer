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
 * Reads the amendments of an act from the answer to {@code amendments.rq}. For each amended
 * location it reads the role, the days and the languages that the amendment is limited to. The role
 * is a code from the authority table {@code fd_375}. It keeps a role it does not know as {@code
 * OTHER} and logs a warning. A placeholder for a day becomes no day.
 */
final class CellarAmendments {
  private static final Logger LOG = LoggerFactory.getLogger(CellarAmendments.class);

  /**
   * An authority code as Cellar writes it in an annotation. For example, {@code
   * {R|http://…/fd_375/R}}.
   */
  private static final Pattern CODE = Pattern.compile("\\{([^|}]*)\\|[^}]*}");

  private CellarAmendments() {}

  /**
   * Returns the amendments in the answer, in the order of the answer. It leaves out amendments by a
   * subject that is not an act.
   */
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
   * Reads Cellar's list of languages as ISO 639-1 codes, for example {@code SV, PL}. An empty list
   * gives no languages. It leaves out codes it does not know.
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

  /**
   * Returns the code of an authority value, for example {@code R} for {@code {R|…}}. If the value
   * is not an authority value, it returns the value.
   */
  private static String code(String value) {
    var matcher = CODE.matcher(value);
    return matcher.find() ? matcher.group(1) : value;
  }
}
