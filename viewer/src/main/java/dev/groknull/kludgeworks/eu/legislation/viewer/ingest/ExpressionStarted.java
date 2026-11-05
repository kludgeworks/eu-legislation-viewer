// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A worker started building an expression: its Formex being fetched and converted. Each attempt
 * starts it once.
 *
 * @param attempt which attempt this is, from 1
 */
@DomainEvent
public record ExpressionStarted(String celex, Language language, int attempt) {}
