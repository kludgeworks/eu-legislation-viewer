// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * The text of a version in a language is converted and stored. The content module has it, by the
 * CELEX and the language.
 */
@DomainEvent
public record TextConverted(String celex, Language language) {}
