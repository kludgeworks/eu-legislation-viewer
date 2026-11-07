// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An attempt to build an expression failed, and a new attempt may succeed. For example, Cellar did
 * not answer, or the store was down. The expression is back in the queue, to be tried again a
 * little later.
 *
 * @param attempt the attempt that failed, from 1
 * @param detail what went wrong, for a person
 */
@DomainEvent
public record ExpressionDeferred(String celex, Language language, int attempt, String detail) {}
