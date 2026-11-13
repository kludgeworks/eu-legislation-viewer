// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * Cellar has a version of a held act in a language that the catalogue did not have it in. A new
 * consolidation reaches its languages one by one, over hours. The catalogue prepares its text in
 * the language.
 *
 * @param act the act
 * @param celex the document that holds the version's text
 * @param language the language
 */
@DomainEvent
public record LanguageAdded(ActIdentifier act, String celex, Language language) {}
