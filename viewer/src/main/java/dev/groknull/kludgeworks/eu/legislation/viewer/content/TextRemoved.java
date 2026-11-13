// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A text was deleted, because its version was withdrawn ({@link VersionWithdrawn}). No text
 * replaces it. A module that keeps something by the text's identity deletes it.
 *
 * @param text the identity of the text, which is no longer stored
 * @param celex the version of the text
 * @param language the language of the text
 */
@DomainEvent
public record TextRemoved(TextId text, String celex, Language language) {}
