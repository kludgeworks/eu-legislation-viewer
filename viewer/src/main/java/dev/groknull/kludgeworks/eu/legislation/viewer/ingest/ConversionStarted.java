// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A worker started to convert the text of a version in a language. It fetches the Formex from
 * Cellar, converts it and stores the text. Each attempt starts once.
 *
 * @param attempt which attempt this is, from 1
 */
@DomainEvent
public record ConversionStarted(String celex, Language language, int attempt) {}
