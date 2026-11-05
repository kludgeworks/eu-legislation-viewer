// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An attempt at building an expression failed in a way trying again may mend (Cellar didn't answer,
 * the store was down): it is back in the queue, to be tried again a little later.
 *
 * @param attempt the attempt that failed, from 1
 * @param detail what went wrong, for a person
 */
@DomainEvent
public record ExpressionDeferred(String celex, Language language, int attempt, String detail) {}
