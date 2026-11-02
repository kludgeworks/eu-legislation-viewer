// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import org.jmolecules.event.annotation.DomainEvent;

/**
 * An expression couldn't be built, and won't be by trying again.
 *
 * @param detail what went wrong, for a person: the converter's refusal, the last error
 */
@DomainEvent
public record ExpressionFailed(String celex, Language language, Failure failure, String detail) {}
