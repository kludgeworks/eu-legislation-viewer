// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * A worker started to build an expression. The worker fetches and converts its Formex. Each attempt
 * starts the expression once.
 *
 * @param attempt which attempt this is, from 1
 */
@DomainEvent
public record ExpressionStarted(String celex, Language language, int attempt) {}
