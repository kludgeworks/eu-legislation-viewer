// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * Cellar republished the Formex of a version of a held act in a language: the fixity of its files
 * changed ({@link Version.Formex#republishedAs}). For example, an interim consolidation was made
 * final, or a wrong amount was corrected. The catalogue prepares the text in the language again.
 * The text shown until then stays, until the new text replaces it.
 *
 * @param act the act
 * @param celex the document that holds the version's text
 * @param language the language
 * @param modified when Cellar modified the Formex
 */
@DomainEvent
public record VersionRepublished(
    ActIdentifier act, String celex, Language language, Instant modified) {}
