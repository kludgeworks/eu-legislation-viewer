// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Set;
import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.modulith.events.Externalized;

/**
 * Cellar has a version of a held act that the catalogue did not have, for example a new
 * consolidation. The catalogue stored it, and prepares its text in each of its languages.
 *
 * <p>It is broadcast to every instance on the {@code version} stream, so that the pages open on the
 * act can tell their readers that a newer version exists.
 *
 * @param act the act
 * @param celex the document that holds the version's text
 * @param date the day the version applies from
 * @param languages the languages its text is in as Formex
 */
@DomainEvent
@Externalized("version")
public record VersionAdded(
    ActIdentifier act, String celex, LocalDate date, Set<Language> languages) {

  public VersionAdded {
    languages = Set.copyOf(languages);
  }
}
